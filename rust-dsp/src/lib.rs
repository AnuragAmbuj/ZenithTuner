//! ZenithTuner Native DSP Core (rust-dsp)
//!
//! Acoustic correction pipeline specifically engineered for the boAt Nirvana Zenith (Mystique Black).
//! Uses Robert Bristow-Johnson (RBJ) Audio EQ Cookbook biquad filters in double precision (f64),
//! zero-allocation execution, strict Nyquist input clamping, and panic safety.

use std::f64::consts::PI;
use std::os::raw::{c_float, c_int, c_void};

pub const TOTAL_BANDS: usize = 64;
pub const PRE_EQ_BANDS: usize = 32;
pub const POST_EQ_BANDS: usize = 32;
pub const NUM_STAGES: usize = 8;
pub const MIN_FREQ: f64 = 20.0;
pub const MAX_FREQ: f64 = 20000.0;
pub const JNI_MIN_BUFFER_SIZE: usize = 129;
pub const JNI_FULL_BUFFER_SIZE: usize = 194;

/// Normalized biquad filter transfer function coefficients:
/// H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BiquadFilter {
    pub b0: f64,
    pub b1: f64,
    pub b2: f64,
    pub a1: f64,
    pub a2: f64,
}

impl BiquadFilter {
    /// Identity passthrough filter
    pub const fn identity() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
        }
    }

    /// Robert Bristow-Johnson High-Pass Filter (HPF)
    pub fn high_pass(freq: f64, q: f64, sample_rate: f64) -> Self {
        let (freq, q, sr) = sanitize_parameters(freq, q, 0.0, sample_rate);
        let omega = 2.0 * PI * freq / sr;
        let cos_w = omega.cos();
        let sin_w = omega.sin();
        let alpha = sin_w / (2.0 * q);

        let a0 = 1.0 + alpha;
        if a0.abs() < 1e-9 || !a0.is_finite() {
            return Self::identity();
        }

        let b0 = (1.0 + cos_w) / (2.0 * a0);
        let b1 = -(1.0 + cos_w) / a0;
        let b2 = (1.0 + cos_w) / (2.0 * a0);
        let a1 = (-2.0 * cos_w) / a0;
        let a2 = (1.0 - alpha) / a0;

        Self { b0, b1, b2, a1, a2 }
    }

    /// Robert Bristow-Johnson Low-Shelf Filter
    pub fn low_shelf(freq: f64, q: f64, gain_db: f64, sample_rate: f64) -> Self {
        let (freq, q, sr) = sanitize_parameters(freq, q, gain_db, sample_rate);
        let gain_db = gain_db.clamp(-36.0, 36.0);
        if gain_db.abs() < 1e-4 {
            return Self::identity();
        }

        let a_val = 10.0_f64.powf(gain_db / 40.0);
        let omega = 2.0 * PI * freq / sr;
        let cos_w = omega.cos();
        let sin_w = omega.sin();
        let beta = (a_val).sqrt() * sin_w / q;

        let a0 = (a_val + 1.0) + (a_val - 1.0) * cos_w + beta;
        if a0.abs() < 1e-9 || !a0.is_finite() {
            return Self::identity();
        }

        let b0 = (a_val * ((a_val + 1.0) - (a_val - 1.0) * cos_w + beta)) / a0;
        let b1 = (2.0 * a_val * ((a_val - 1.0) - (a_val + 1.0) * cos_w)) / a0;
        let b2 = (a_val * ((a_val + 1.0) - (a_val - 1.0) * cos_w - beta)) / a0;
        let a1 = (-2.0 * ((a_val - 1.0) + (a_val + 1.0) * cos_w)) / a0;
        let a2 = ((a_val + 1.0) + (a_val - 1.0) * cos_w - beta) / a0;

        Self { b0, b1, b2, a1, a2 }
    }

    /// Robert Bristow-Johnson Peaking EQ Filter
    pub fn peaking_eq(freq: f64, q: f64, gain_db: f64, sample_rate: f64) -> Self {
        let (freq, q, sr) = sanitize_parameters(freq, q, gain_db, sample_rate);
        let gain_db = gain_db.clamp(-36.0, 36.0);
        if gain_db.abs() < 1e-4 {
            return Self::identity();
        }

        let a_val = 10.0_f64.powf(gain_db / 40.0);
        let omega = 2.0 * PI * freq / sr;
        let cos_w = omega.cos();
        let sin_w = omega.sin();
        let alpha = sin_w / (2.0 * q);

        let a0 = 1.0 + alpha / a_val;
        if a0.abs() < 1e-9 || !a0.is_finite() {
            return Self::identity();
        }

        let b0 = (1.0 + alpha * a_val) / a0;
        let b1 = (-2.0 * cos_w) / a0;
        let b2 = (1.0 - alpha * a_val) / a0;
        let a1 = (-2.0 * cos_w) / a0;
        let a2 = (1.0 - alpha / a_val) / a0;

        Self { b0, b1, b2, a1, a2 }
    }

    /// Robert Bristow-Johnson High-Shelf Filter
    pub fn high_shelf(freq: f64, q: f64, gain_db: f64, sample_rate: f64) -> Self {
        let (freq, q, sr) = sanitize_parameters(freq, q, gain_db, sample_rate);
        let gain_db = gain_db.clamp(-36.0, 36.0);
        if gain_db.abs() < 1e-4 {
            return Self::identity();
        }

        let a_val = 10.0_f64.powf(gain_db / 40.0);
        let omega = 2.0 * PI * freq / sr;
        let cos_w = omega.cos();
        let sin_w = omega.sin();
        let beta = (a_val).sqrt() * sin_w / q;

        let a0 = (a_val + 1.0) - (a_val - 1.0) * cos_w + beta;
        if a0.abs() < 1e-9 || !a0.is_finite() {
            return Self::identity();
        }

        let b0 = (a_val * ((a_val + 1.0) + (a_val - 1.0) * cos_w + beta)) / a0;
        let b1 = (-2.0 * a_val * ((a_val - 1.0) + (a_val + 1.0) * cos_w)) / a0;
        let b2 = (a_val * ((a_val + 1.0) + (a_val - 1.0) * cos_w - beta)) / a0;
        let a1 = (2.0 * ((a_val - 1.0) - (a_val + 1.0) * cos_w)) / a0;
        let a2 = ((a_val + 1.0) - (a_val - 1.0) * cos_w - beta) / a0;

        Self { b0, b1, b2, a1, a2 }
    }

    /// Evaluates the frequency response magnitude in decibels (dB) at frequency `f` (Hz)
    pub fn magnitude_db(&self, f: f64, sample_rate: f64) -> f64 {
        if !f.is_finite() || f <= 0.0 {
            return 0.0;
        }
        let sr = sample_rate.clamp(8000.0, 384000.0);
        let f_clamped = f.clamp(1.0, sr * 0.499);
        let omega = 2.0 * PI * f_clamped / sr;
        let cos_w = omega.cos();
        let sin_w = omega.sin();
        let cos_2w = (2.0 * omega).cos();
        let sin_2w = (2.0 * omega).sin();

        // Numerator: b0 + b1*e^(-jw) + b2*e^(-j2w)
        let num_re = self.b0 + self.b1 * cos_w + self.b2 * cos_2w;
        let num_im = -self.b1 * sin_w - self.b2 * sin_2w;

        // Denominator: 1.0 + a1*e^(-jw) + a2*e^(-j2w)
        let den_re = 1.0 + self.a1 * cos_w + self.a2 * cos_2w;
        let den_im = -self.a1 * sin_w - self.a2 * sin_2w;

        let num_mag_sq = num_re * num_re + num_im * num_im;
        let den_mag_sq = den_re * den_re + den_im * den_im;

        if den_mag_sq < 1e-12 || !den_mag_sq.is_finite() {
            return 0.0;
        }

        let mag_sq = num_mag_sq / den_mag_sq;
        if mag_sq <= 1e-12 || !mag_sq.is_finite() {
            return -120.0;
        }

        10.0 * mag_sq.log10()
    }
}

/// Strictly clamps and validates all input filter parameters, preventing Nyquist blowup.
#[inline]
fn sanitize_parameters(freq: f64, q: f64, _gain_db: f64, sample_rate: f64) -> (f64, f64, f64) {
    let sr = if sample_rate.is_finite() && sample_rate >= 8000.0 {
        sample_rate.clamp(8000.0, 384000.0)
    } else {
        44100.0
    };

    let max_freq = sr * 0.499;
    let f = if freq.is_finite() && freq >= 10.0 {
        freq.clamp(10.0, max_freq)
    } else {
        1000.0
    };

    let q_clamped = if q.is_finite() && q >= 0.05 {
        q.clamp(0.05, 50.0)
    } else {
        0.7071
    };

    (f, q_clamped, sr)
}

/// Acoustic filter stage type definition
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum FilterType {
    Pass = 0,
    HighPass = 1,
    LowShelf = 2,
    Peaking = 3,
    HighShelf = 4,
}

impl FilterType {
    pub fn from_u8(val: u8) -> Self {
        match val {
            1 => FilterType::HighPass,
            2 => FilterType::LowShelf,
            3 => FilterType::Peaking,
            4 => FilterType::HighShelf,
            _ => FilterType::Pass,
        }
    }
}

/// Configuration for a single parametric stage
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ParametricStage {
    pub enabled: bool,
    pub filter_type: FilterType,
    pub freq: f64,
    pub q: f64,
    pub gain_db: f64,
}

impl ParametricStage {
    pub fn to_biquad(&self, sample_rate: f64) -> BiquadFilter {
        if !self.enabled {
            return BiquadFilter::identity();
        }
        match self.filter_type {
            FilterType::Pass => BiquadFilter::identity(),
            FilterType::HighPass => BiquadFilter::high_pass(self.freq, self.q, sample_rate),
            FilterType::LowShelf => BiquadFilter::low_shelf(self.freq, self.q, self.gain_db, sample_rate),
            FilterType::Peaking => BiquadFilter::peaking_eq(self.freq, self.q, self.gain_db, sample_rate),
            FilterType::HighShelf => BiquadFilter::high_shelf(self.freq, self.q, self.gain_db, sample_rate),
        }
    }
}

/// Complete customizable tuning specification
#[derive(Debug, Clone)]
pub struct CustomTuningSpec {
    pub stages: [ParametricStage; NUM_STAGES],
    pub crossfeed_amount: f64,
    pub manual_preamp_override: Option<f64>,
}

impl CustomTuningSpec {
    /// Default baseline target for boAt Nirvana Zenith
    pub fn default_target(
        anc_anti_mud: bool,
        sub_bass_weight: f32,
        vocal_presence: f32,
    ) -> Self {
        let sub_scale = (sub_bass_weight as f64).clamp(0.0, 2.0);
        let vocal_scale = (vocal_presence as f64).clamp(0.0, 2.0);
        let mid_bass_gain = if anc_anti_mud { -5.2 } else { -1.5 };

        let stages = [
            // Stage 0: HPF 24Hz
            ParametricStage {
                enabled: true,
                filter_type: FilterType::HighPass,
                freq: 24.0,
                q: 0.7071,
                gain_db: 0.0,
            },
            // Stage 1: Low-Shelf 55Hz
            ParametricStage {
                enabled: true,
                filter_type: FilterType::LowShelf,
                freq: 55.0,
                q: 0.71,
                gain_db: 3.6 * sub_scale,
            },
            // Stage 2: Peaking 145Hz (50dB ANC Anti-Mud)
            ParametricStage {
                enabled: true,
                filter_type: FilterType::Peaking,
                freq: 145.0,
                q: 1.8,
                gain_db: mid_bass_gain,
            },
            // Stage 3: Peaking 320Hz Boxiness Cut
            ParametricStage {
                enabled: true,
                filter_type: FilterType::Peaking,
                freq: 320.0,
                q: 1.2,
                gain_db: -2.4,
            },
            // Stage 4: Peaking 1650Hz Vocal Forwardness
            ParametricStage {
                enabled: true,
                filter_type: FilterType::Peaking,
                freq: 1650.0,
                q: 1.4,
                gain_db: 2.8 * vocal_scale,
            },
            // Stage 5: Peaking 3100Hz Pinna Ear-Gain
            ParametricStage {
                enabled: true,
                filter_type: FilterType::Peaking,
                freq: 3100.0,
                q: 1.8,
                gain_db: 3.2 * vocal_scale,
            },
            // Stage 6: Peaking 6600Hz AAC Treble De-Sizzle Notch
            ParametricStage {
                enabled: true,
                filter_type: FilterType::Peaking,
                freq: 6600.0,
                q: 2.5,
                gain_db: -3.8,
            },
            // Stage 7: High-Shelf 11500Hz Air & Soundstage
            ParametricStage {
                enabled: true,
                filter_type: FilterType::HighShelf,
                freq: 11500.0,
                q: 0.71,
                gain_db: 2.5,
            },
        ];

        Self {
            stages,
            crossfeed_amount: 0.0,
            manual_preamp_override: None,
        }
    }

    /// Decodes flat parameter slice (40 floats = 8 stages * 5 values + optional crossfeed & preamp)
    pub fn from_slice(params: &[f32]) -> Self {
        let mut stages = [ParametricStage {
            enabled: true,
            filter_type: FilterType::Pass,
            freq: 1000.0,
            q: 0.7071,
            gain_db: 0.0,
        }; NUM_STAGES];

        for i in 0..NUM_STAGES {
            let offset = i * 5;
            if offset + 4 < params.len() {
                let enabled = params[offset] > 0.5;
                let filter_type = FilterType::from_u8(params[offset + 1].round() as u8);
                let freq = (params[offset + 2] as f64).clamp(10.0, 20000.0);
                let q = (params[offset + 3] as f64).clamp(0.05, 50.0);
                let gain_db = (params[offset + 4] as f64).clamp(-36.0, 36.0);

                stages[i] = ParametricStage {
                    enabled,
                    filter_type,
                    freq,
                    q,
                    gain_db,
                };
            }
        }

        let crossfeed_amount = if params.len() > 40 {
            (params[40] as f64).clamp(0.0, 1.0)
        } else {
            0.0
        };

        let manual_preamp_override = if params.len() > 41 && params[41] > -90.0 {
            Some((params[41] as f64).clamp(-24.0, 6.0))
        } else {
            None
        };

        Self {
            stages,
            crossfeed_amount,
            manual_preamp_override,
        }
    }
}

/// Generates the 64 logarithmically spaced frequencies across 20Hz - 20000Hz
pub fn generate_log_frequencies() -> [f64; TOTAL_BANDS] {
    let mut freqs = [0.0; TOTAL_BANDS];
    let ratio = MAX_FREQ / MIN_FREQ;
    for i in 0..TOTAL_BANDS {
        let exponent = (i as f64) / ((TOTAL_BANDS - 1) as f64);
        freqs[i] = MIN_FREQ * ratio.powf(exponent);
    }
    freqs
}

/// Result of acoustic correction computation
#[derive(Debug, Clone)]
pub struct DspComputationResult {
    pub pre_eq_freqs: [f32; PRE_EQ_BANDS],
    pub pre_eq_gains: [f32; PRE_EQ_BANDS],
    pub post_eq_freqs: [f32; POST_EQ_BANDS],
    pub post_eq_gains: [f32; POST_EQ_BANDS],
    pub preamp_headroom_db: f32,
    pub max_boost_db: f32,
    pub full_curve_gains_db: [f32; TOTAL_BANDS],
}

/// Evaluates combined response with optional crossfeed acoustic simulation
pub fn evaluate_spec_db(spec: &CustomTuningSpec, biquads: &[BiquadFilter; NUM_STAGES], f: f64, sr: f64) -> f64 {
    let mut total_db = 0.0;
    for biquad in biquads {
        total_db += biquad.magnitude_db(f, sr);
    }

    if spec.crossfeed_amount > 0.01 {
        let xf = spec.crossfeed_amount;
        if f < 700.0 {
            let boost = 0.75 * xf * (1.0 - f / 700.0);
            total_db += boost;
        } else if f > 6000.0 {
            let treble_calm = -0.6 * xf * ((f - 6000.0) / 14000.0).clamp(0.0, 1.0);
            total_db += treble_calm;
        }
    }

    total_db
}

/// Core DSP computation function with full customization support.
pub fn compute_custom_dsp(
    sample_rate: f64,
    spec: &CustomTuningSpec,
) -> DspComputationResult {
    let sr = sample_rate.clamp(8000.0, 384000.0);
    let mut biquads = [BiquadFilter::identity(); NUM_STAGES];
    for (i, stage) in spec.stages.iter().enumerate() {
        biquads[i] = stage.to_biquad(sr);
    }

    let freqs = generate_log_frequencies();
    let mut full_curve = [0.0f32; TOTAL_BANDS];
    let mut max_boost = 0.0f64;

    for (i, &f) in freqs.iter().enumerate() {
        let gain_db = evaluate_spec_db(spec, &biquads, f, sr);
        full_curve[i] = gain_db as f32;
        if gain_db > max_boost {
            max_boost = gain_db;
        }
    }

    // High-resolution scan across 128 intermediate frequencies
    for step in 0..128 {
        let exp = (step as f64) / 127.0;
        let test_f = MIN_FREQ * (MAX_FREQ / MIN_FREQ).powf(exp);
        let g = evaluate_spec_db(spec, &biquads, test_f, sr);
        if g > max_boost {
            max_boost = g;
        }
    }

    let preamp_headroom_db = match spec.manual_preamp_override {
        Some(manual) => manual as f32,
        None => {
            if max_boost > 0.0 {
                -(max_boost + 0.2) as f32
            } else {
                0.0f32
            }
        }
    };

    let mut pre_eq_freqs = [0.0f32; PRE_EQ_BANDS];
    let mut pre_eq_gains = [0.0f32; PRE_EQ_BANDS];
    let mut post_eq_freqs = [0.0f32; POST_EQ_BANDS];
    let mut post_eq_gains = [0.0f32; POST_EQ_BANDS];

    for i in 0..PRE_EQ_BANDS {
        pre_eq_freqs[i] = freqs[i] as f32;
        pre_eq_gains[i] = full_curve[i];
    }

    for i in 0..POST_EQ_BANDS {
        post_eq_freqs[i] = freqs[PRE_EQ_BANDS + i] as f32;
        post_eq_gains[i] = full_curve[PRE_EQ_BANDS + i];
    }

    DspComputationResult {
        pre_eq_freqs,
        pre_eq_gains,
        post_eq_freqs,
        post_eq_gains,
        preamp_headroom_db,
        max_boost_db: max_boost as f32,
        full_curve_gains_db: full_curve,
    }
}

/// Baseline computation function
pub fn compute_dsp_parameters(
    sample_rate: f64,
    anc_anti_mud: bool,
    sub_bass_weight: f32,
    vocal_presence: f32,
) -> DspComputationResult {
    let spec = CustomTuningSpec::default_target(anc_anti_mud, sub_bass_weight, vocal_presence);
    compute_custom_dsp(sample_rate, &spec)
}

/// Serializes computation into output buffer
pub fn write_dsp_result_to_slice(result: &DspComputationResult, out: &mut [f32]) {
    if out.len() < JNI_MIN_BUFFER_SIZE {
        return;
    }

    out[0..32].copy_from_slice(&result.pre_eq_freqs);
    out[32..64].copy_from_slice(&result.pre_eq_gains);
    out[64..96].copy_from_slice(&result.post_eq_freqs);
    out[96..128].copy_from_slice(&result.post_eq_gains);
    out[128] = result.preamp_headroom_db;

    if out.len() >= 130 {
        out[129] = result.max_boost_db;
    }
    if out.len() >= 194 {
        out[130..194].copy_from_slice(&result.full_curve_gains_db);
    }
}

// ============================================================================
// C and JNI FFI Exports
// ============================================================================

#[no_mangle]
pub unsafe extern "C" fn zenith_dsp_compute(
    sample_rate: f64,
    anc_anti_mud: c_int,
    sub_bass_weight: c_float,
    vocal_presence: c_float,
    output_buffer: *mut c_float,
    buffer_len: c_int,
) -> c_int {
    if output_buffer.is_null() || buffer_len < JNI_MIN_BUFFER_SIZE as c_int {
        return -1;
    }

    let out_slice = std::slice::from_raw_parts_mut(output_buffer, buffer_len as usize);
    let result = compute_dsp_parameters(
        sample_rate,
        anc_anti_mud != 0,
        sub_bass_weight,
        vocal_presence,
    );
    write_dsp_result_to_slice(&result, out_slice);
    0
}

#[repr(C)]
pub struct JNINativeInterface {
    reserved0: *mut c_void,
    reserved1: *mut c_void,
    reserved2: *mut c_void,
    reserved3: *mut c_void,
    table: [*mut c_void; 256],
}

pub type JNIEnv = *mut JNINativeInterface;
pub type JObject = *mut c_void;
pub type JClass = JObject;
pub type JFloatArray = JObject;
pub type JSize = i32;

/// Standard JNI exported function:
/// package: com.zenith.tuner.dsp
/// class: ZenithNativeDsp
/// method: computeAcousticCorrection
#[no_mangle]
pub unsafe extern "system" fn Java_com_zenith_tuner_dsp_ZenithNativeDsp_computeAcousticCorrection(
    env: *mut JNIEnv,
    _class: JClass,
    sample_rate: f64,
    anc_anti_mud: u8,
    sub_bass_weight: f32,
    vocal_presence: f32,
    output_buffer: JFloatArray,
) -> c_int {
    std::panic::catch_unwind(|| {
        if env.is_null() || (*env).is_null() || output_buffer.is_null() {
            return -1;
        }

        let jni_interface = *env;
        let get_array_length_ptr = (*jni_interface).table[171 - 4];
        let get_float_elements_ptr = (*jni_interface).table[189 - 4];
        let release_float_elements_ptr = (*jni_interface).table[197 - 4];

        if get_array_length_ptr.is_null()
            || get_float_elements_ptr.is_null()
            || release_float_elements_ptr.is_null()
        {
            return -2;
        }

        let get_array_length: unsafe extern "system" fn(*mut JNIEnv, JFloatArray) -> JSize =
            std::mem::transmute(get_array_length_ptr);
        let get_float_elements: unsafe extern "system" fn(
            *mut JNIEnv,
            JFloatArray,
            *mut u8,
        ) -> *mut f32 = std::mem::transmute(get_float_elements_ptr);
        let release_float_elements: unsafe extern "system" fn(
            *mut JNIEnv,
            JFloatArray,
            *mut f32,
            i32,
        ) = std::mem::transmute(release_float_elements_ptr);

        let len = get_array_length(env, output_buffer);
        if len < JNI_MIN_BUFFER_SIZE as JSize {
            return -3;
        }

        let mut is_copy: u8 = 0;
        let elems_ptr = get_float_elements(env, output_buffer, &mut is_copy);
        if elems_ptr.is_null() {
            return -4;
        }

        let buffer_slice = std::slice::from_raw_parts_mut(elems_ptr, len as usize);
        let result = compute_dsp_parameters(
            sample_rate,
            anc_anti_mud != 0,
            sub_bass_weight,
            vocal_presence,
        );

        write_dsp_result_to_slice(&result, buffer_slice);
        release_float_elements(env, output_buffer, elems_ptr, 0);
        0
    })
    .unwrap_or(-99)
}

/// Advanced JNI function for custom parametric tuning:
/// method: computeCustomTuning(sampleRate, paramsArray, outputBuffer)
#[no_mangle]
pub unsafe extern "system" fn Java_com_zenith_tuner_dsp_ZenithNativeDsp_computeCustomTuning(
    env: *mut JNIEnv,
    _class: JClass,
    sample_rate: f64,
    params_array: JFloatArray,
    output_buffer: JFloatArray,
) -> c_int {
    std::panic::catch_unwind(|| {
        if env.is_null() || (*env).is_null() || params_array.is_null() || output_buffer.is_null() {
            return -1;
        }

        let jni_interface = *env;
        let get_array_length_ptr = (*jni_interface).table[171 - 4];
        let get_float_elements_ptr = (*jni_interface).table[189 - 4];
        let release_float_elements_ptr = (*jni_interface).table[197 - 4];

        if get_array_length_ptr.is_null()
            || get_float_elements_ptr.is_null()
            || release_float_elements_ptr.is_null()
        {
            return -2;
        }

        let get_array_length: unsafe extern "system" fn(*mut JNIEnv, JFloatArray) -> JSize =
            std::mem::transmute(get_array_length_ptr);
        let get_float_elements: unsafe extern "system" fn(
            *mut JNIEnv,
            JFloatArray,
            *mut u8,
        ) -> *mut f32 = std::mem::transmute(get_float_elements_ptr);
        let release_float_elements: unsafe extern "system" fn(
            *mut JNIEnv,
            JFloatArray,
            *mut f32,
            i32,
        ) = std::mem::transmute(release_float_elements_ptr);

        let params_len = get_array_length(env, params_array);
        let out_len = get_array_length(env, output_buffer);
        if out_len < JNI_MIN_BUFFER_SIZE as JSize {
            return -3;
        }

        let mut is_copy_p: u8 = 0;
        let params_ptr = get_float_elements(env, params_array, &mut is_copy_p);
        if params_ptr.is_null() {
            return -4;
        }

        let params_slice = std::slice::from_raw_parts(params_ptr, params_len as usize);
        let spec = CustomTuningSpec::from_slice(params_slice);
        release_float_elements(env, params_array, params_ptr, 0);

        let mut is_copy_o: u8 = 0;
        let out_ptr = get_float_elements(env, output_buffer, &mut is_copy_o);
        if out_ptr.is_null() {
            return -5;
        }

        let buffer_slice = std::slice::from_raw_parts_mut(out_ptr, out_len as usize);
        let result = compute_custom_dsp(sample_rate, &spec);
        write_dsp_result_to_slice(&result, buffer_slice);
        release_float_elements(env, output_buffer, out_ptr, 0);
        0
    })
    .unwrap_or(-99)
}

// ============================================================================
// Unit Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_identity_filter() {
        let f = BiquadFilter::identity();
        assert_eq!(f.magnitude_db(100.0, 48000.0), 0.0);
        assert_eq!(f.magnitude_db(1000.0, 48000.0), 0.0);
    }

    #[test]
    fn test_peaking_filter_gain() {
        let filter = BiquadFilter::peaking_eq(1000.0, 2.0, 6.0, 48000.0);
        let center_gain = filter.magnitude_db(1000.0, 48000.0);
        assert!((center_gain - 6.0).abs() < 0.2);

        let far_gain = filter.magnitude_db(50.0, 48000.0);
        assert!(far_gain.abs() < 0.2);
    }

    #[test]
    fn test_high_pass_filter_attenuation() {
        let hpf = BiquadFilter::high_pass(24.0, 0.7071, 48000.0);
        let sub_rumble = hpf.magnitude_db(5.0, 48000.0);
        assert!(sub_rumble < -15.0);

        let audio_band = hpf.magnitude_db(1000.0, 48000.0);
        assert!(audio_band.abs() < 0.05);
    }

    #[test]
    fn test_low_shelf_filter() {
        let shelf = BiquadFilter::low_shelf(55.0, 0.71, 3.6, 48000.0);
        let sub_bass = shelf.magnitude_db(30.0, 48000.0);
        assert!((sub_bass - 3.6).abs() < 0.4);

        let high_freq = shelf.magnitude_db(5000.0, 48000.0);
        assert!(high_freq.abs() < 0.2);
    }

    #[test]
    fn test_acoustic_target_anti_mud_cut() {
        let sample_rate = 44100.0;
        let res_with = compute_dsp_parameters(sample_rate, true, 1.0, 1.0);
        let res_without = compute_dsp_parameters(sample_rate, false, 1.0, 1.0);

        let mid_bass_with = res_with.pre_eq_gains[14]; // near 145Hz
        let mid_bass_without = res_without.pre_eq_gains[14];

        assert!(mid_bass_with < mid_bass_without);
    }

    #[test]
    fn test_aac_treble_harshness_notch() {
        let sample_rate = 44100.0;
        let res = compute_dsp_parameters(sample_rate, true, 1.0, 1.0);

        // Band around 6681Hz is post_eq index 21
        let notch_gain = res.post_eq_gains[21];
        let adjacent_gain = res.post_eq_gains[18]; // ~4800Hz
        assert!(notch_gain < 0.0);
        assert!(notch_gain < adjacent_gain);
    }

    #[test]
    fn test_preamp_headroom_guarantees_zero_clipping() {
        let sample_rate = 48000.0;
        let result = compute_dsp_parameters(sample_rate, true, 1.5, 1.5);

        if result.max_boost_db > 0.0 {
            assert!(result.preamp_headroom_db < -result.max_boost_db);
            let max_possible_output = result.max_boost_db + result.preamp_headroom_db;
            assert!(max_possible_output <= 0.0);
        }
    }

    #[test]
    fn test_custom_parametric_tuning() {
        let mut custom_spec = CustomTuningSpec::default_target(true, 1.0, 1.0);
        custom_spec.stages[1].gain_db = 6.0;
        custom_spec.crossfeed_amount = 0.5;

        let result = compute_custom_dsp(48000.0, &custom_spec);
        assert!(result.max_boost_db > 2.0);
        assert!(result.preamp_headroom_db <= -result.max_boost_db);
    }

    #[test]
    fn test_manual_preamp_override() {
        let mut custom_spec = CustomTuningSpec::default_target(true, 1.0, 1.0);
        custom_spec.manual_preamp_override = Some(-5.0);

        let result = compute_custom_dsp(48000.0, &custom_spec);
        assert_eq!(result.preamp_headroom_db, -5.0);
    }

    #[test]
    fn test_c_api_safety() {
        let mut buffer = [0.0f32; 194];
        let status = unsafe {
            zenith_dsp_compute(
                48000.0,
                1,
                1.0,
                1.0,
                buffer.as_mut_ptr(),
                buffer.len() as c_int,
            )
        };
        assert_eq!(status, 0);
        assert_eq!(buffer[0], 20.0);
    }
}
