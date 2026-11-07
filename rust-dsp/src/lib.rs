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
pub const MIN_FREQ: f64 = 20.0;
pub const MAX_FREQ: f64 = 20000.0;
pub const JNI_MIN_BUFFER_SIZE: usize = 129; // 32 freqs + 32 gains + 32 freqs + 32 gains + 1 preamp

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

    // Nyquist limit safety margin: max frequency is 0.499 * sr
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

/// The 8-Stage Acoustic Correction Target for boAt Nirvana Zenith (Mystique Black)
pub struct ZenithAcousticTarget {
    pub filters: [BiquadFilter; 8],
}

impl ZenithAcousticTarget {
    /// Builds the 8 biquad filters according to the engineering diagnosis:
    /// 1. High-Pass (24Hz): Strips sub-audible rumble under 50dB ANC
    /// 2. Low-Shelf (55Hz): Restores clean sub-bass punch and slam
    /// 3. Peaking (145Hz, -5.2dB): Surgically cuts 11mm mid-bass resonance and 50dB ANC feedback
    /// 4. Peaking (320Hz, -2.4dB): Clears lower-mid boxiness
    /// 5. Peaking (1650Hz, +2.8dB): Pulls recessed vocals forward
    /// 6. Peaking (3100Hz, +3.2dB): Restores pinna ear-gain
    /// 7. Peaking (6600Hz, -3.8dB): Notches out metallic AAC treble harshness
    /// 8. High-Shelf (11500Hz, +2.5dB): Opens up air and soundstage width
    pub fn new(
        sample_rate: f64,
        anc_anti_mud: bool,
        sub_bass_weight: f32,
        vocal_presence: f32,
    ) -> Self {
        let sub_bass_scale = (sub_bass_weight as f64).clamp(0.0, 2.0);
        let vocal_scale = (vocal_presence as f64).clamp(0.0, 2.0);

        // 1. High-Pass 24Hz, Q = 0.707
        let f1 = BiquadFilter::high_pass(24.0, 0.7071, sample_rate);

        // 2. Low-Shelf 55Hz, base +3.6dB modulated by sub_bass_weight
        let sub_bass_gain = 3.6 * sub_bass_scale;
        let f2 = BiquadFilter::low_shelf(55.0, 0.71, sub_bass_gain, sample_rate);

        // 3. Peaking 145Hz, -5.2dB (ANC Anti-Mud). If toggled off, apply light -1.5dB cut for driver resonance
        let mid_bass_gain = if anc_anti_mud { -5.2 } else { -1.5 };
        let f3 = BiquadFilter::peaking_eq(145.0, 1.8, mid_bass_gain, sample_rate);

        // 4. Peaking 320Hz, -2.4dB
        let f4 = BiquadFilter::peaking_eq(320.0, 1.2, -2.4, sample_rate);

        // 5. Peaking 1650Hz, +2.8dB modulated by vocal_presence
        let vocal_1650_gain = 2.8 * vocal_scale;
        let f5 = BiquadFilter::peaking_eq(1650.0, 1.4, vocal_1650_gain, sample_rate);

        // 6. Peaking 3100Hz, +3.2dB modulated by vocal_presence
        let vocal_3100_gain = 3.2 * vocal_scale;
        let f6 = BiquadFilter::peaking_eq(3100.0, 1.8, vocal_3100_gain, sample_rate);

        // 7. Peaking 6600Hz, -3.8dB notch
        let f7 = BiquadFilter::peaking_eq(6600.0, 2.5, -3.8, sample_rate);

        // 8. High-Shelf 11500Hz, +2.5dB
        let f8 = BiquadFilter::high_shelf(11500.0, 0.71, 2.5, sample_rate);

        Self {
            filters: [f1, f2, f3, f4, f5, f6, f7, f8],
        }
    }

    /// Evaluates the total combined response in dB at frequency `f`
    pub fn evaluate_db(&self, f: f64, sample_rate: f64) -> f64 {
        let mut total_db = 0.0;
        for filter in &self.filters {
            total_db += filter.magnitude_db(f, sample_rate);
        }
        total_db
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

/// Core DSP computation function.
/// Fills the 64-band split across Pre-EQ (32) and Post-EQ (32) and computes automatic preamp headroom.
pub fn compute_dsp_parameters(
    sample_rate: f64,
    anc_anti_mud: bool,
    sub_bass_weight: f32,
    vocal_presence: f32,
) -> DspComputationResult {
    let target = ZenithAcousticTarget::new(sample_rate, anc_anti_mud, sub_bass_weight, vocal_presence);
    let freqs = generate_log_frequencies();

    let mut full_curve = [0.0f32; TOTAL_BANDS];
    let mut max_boost = 0.0f64;

    // Evaluate response at 64 logarithmic bands
    for (i, &f) in freqs.iter().enumerate() {
        let gain_db = target.evaluate_db(f, sample_rate);
        full_curve[i] = gain_db as f32;
        if gain_db > max_boost {
            max_boost = gain_db;
        }
    }

    // High-resolution scan across 128 intermediate frequencies to guarantee no hidden peak exceeds headroom
    for step in 0..128 {
        let exp = (step as f64) / 127.0;
        let test_f = MIN_FREQ * (MAX_FREQ / MIN_FREQ).powf(exp);
        let g = target.evaluate_db(test_f, sample_rate);
        if g > max_boost {
            max_boost = g;
        }
    }

    // Automatic Preamp Headroom:
    // If the curve boosts anywhere by +G dB, inject an exact negative preamp (-G - 0.2dB safety)
    // guaranteeing zero digital clipping on Android's fixed/floating pipeline.
    let preamp_headroom_db = if max_boost > 0.0 {
        -(max_boost + 0.2) as f32
    } else {
        0.0f32
    };

    let mut pre_eq_freqs = [0.0f32; PRE_EQ_BANDS];
    let mut pre_eq_gains = [0.0f32; PRE_EQ_BANDS];
    let mut post_eq_freqs = [0.0f32; POST_EQ_BANDS];
    let mut post_eq_gains = [0.0f32; POST_EQ_BANDS];

    // Pre-EQ: Bands 0..31 (20Hz to ~632Hz)
    for i in 0..PRE_EQ_BANDS {
        pre_eq_freqs[i] = freqs[i] as f32;
        pre_eq_gains[i] = full_curve[i];
    }

    // Post-EQ: Bands 32..63 (~694Hz to 20000Hz)
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

/// Serializes computation into a flat float slice.
/// Layout:
/// - [0..31]: Pre-EQ frequencies (32 floats)
/// - [32..63]: Pre-EQ gains (32 floats)
/// - [64..95]: Post-EQ frequencies (32 floats)
/// - [96..127]: Post-EQ gains (32 floats)
/// - [128]: Preamp headroom gain (1 float)
/// - [129]: Max boost dB (1 float, optional)
/// - [130..193]: 64-band full curve gains for UI Bode plot (64 floats, optional)
pub fn write_dsp_result_to_slice(result: &DspComputationResult, out: &mut [f32]) {
    if out.len() < JNI_MIN_BUFFER_SIZE {
        return;
    }

    // Pre-EQ Frequencies
    out[0..32].copy_from_slice(&result.pre_eq_freqs);
    // Pre-EQ Gains
    out[32..64].copy_from_slice(&result.pre_eq_gains);
    // Post-EQ Frequencies
    out[64..96].copy_from_slice(&result.post_eq_freqs);
    // Post-EQ Gains
    out[96..128].copy_from_slice(&result.post_eq_gains);
    // Index 128: Preamp Headroom
    out[128] = result.preamp_headroom_db;

    // Optional metadata and spline visualization curve if buffer allows
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

/// Direct C-compatible API for zero-overhead native integration
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

// JNI Types and table layout
#[repr(C)]
pub struct JNINativeInterface {
    reserved0: *mut c_void,
    reserved1: *mut c_void,
    reserved2: *mut c_void,
    reserved3: *mut c_void,
    // Pointers up to index 213 (indexed dynamically to prevent structure layout errors)
    table: [*mut c_void; 256],
}

pub type JNIEnv = *mut JNINativeInterface;
pub type JObject = *mut c_void;
pub type JClass = JObject;
pub type JFloatArray = JObject;
pub type JSize = i32;

/// JNI exported function:
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
        // Function pointer offsets in JNINativeInterface:
        // Index 171: GetArrayLength(env, array) -> jsize
        // Index 189: GetFloatArrayElements(env, array, isCopy) -> *mut jfloat
        // Index 197: ReleaseFloatArrayElements(env, array, elems, mode) -> void
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

        // 0 mode commits changes back to Java array and frees copy
        release_float_elements(env, output_buffer, elems_ptr, 0);
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
        // Test 1000Hz peaking filter with +6dB
        let filter = BiquadFilter::peaking_eq(1000.0, 2.0, 6.0, 48000.0);
        let center_gain = filter.magnitude_db(1000.0, 48000.0);
        // Center gain should be very close to +6.0 dB
        assert!((center_gain - 6.0).abs() < 0.2);

        // Far away from center frequency, gain should approach 0 dB
        let far_gain = filter.magnitude_db(50.0, 48000.0);
        assert!(far_gain.abs() < 0.2);
    }

    #[test]
    fn test_high_pass_filter_attenuation() {
        // High-pass at 24Hz: frequencies below 10Hz should be attenuated
        let hpf = BiquadFilter::high_pass(24.0, 0.7071, 48000.0);
        let sub_rumble = hpf.magnitude_db(5.0, 48000.0);
        assert!(sub_rumble < -15.0); // significantly cut

        // Frequencies well above 100Hz should pass transparently
        let audio_band = hpf.magnitude_db(1000.0, 48000.0);
        assert!(audio_band.abs() < 0.05);
    }

    #[test]
    fn test_low_shelf_filter() {
        // Low-shelf at 55Hz with +3.6dB boost
        let shelf = BiquadFilter::low_shelf(55.0, 0.71, 3.6, 48000.0);
        let sub_bass = shelf.magnitude_db(30.0, 48000.0);
        assert!((sub_bass - 3.6).abs() < 0.4);

        let high_freq = shelf.magnitude_db(5000.0, 48000.0);
        assert!(high_freq.abs() < 0.2);
    }

    #[test]
    fn test_acoustic_target_anti_mud_cut() {
        let sample_rate = 44100.0;
        let with_anti_mud = ZenithAcousticTarget::new(sample_rate, true, 1.0, 1.0);
        let without_anti_mud = ZenithAcousticTarget::new(sample_rate, false, 1.0, 1.0);

        // At 145Hz (mid-bass resonance under ANC), with_anti_mud should have deeper cut
        let gain_with = with_anti_mud.evaluate_db(145.0, sample_rate);
        let gain_without = without_anti_mud.evaluate_db(145.0, sample_rate);

        assert!(gain_with < gain_without);
        assert!(gain_with < -3.0); // cuts bloat
    }

    #[test]
    fn test_aac_treble_harshness_notch() {
        let sample_rate = 44100.0;
        let target = ZenithAcousticTarget::new(sample_rate, true, 1.0, 1.0);

        // At 6600Hz, we should see a distinct notch
        let gain_6600 = target.evaluate_db(6600.0, sample_rate);
        let gain_4500 = target.evaluate_db(4500.0, sample_rate);

        assert!(gain_6600 < 0.0);
        assert!(gain_6600 < gain_4500); // 6600Hz notched below adjacent bands
    }

    #[test]
    fn test_preamp_headroom_guarantees_zero_clipping() {
        let sample_rate = 48000.0;
        let result = compute_dsp_parameters(sample_rate, true, 1.5, 1.5);

        // If max boost > 0, preamp headroom MUST be negative and cover the max boost
        if result.max_boost_db > 0.0 {
            assert!(result.preamp_headroom_db < -result.max_boost_db);
            // Combined digital sum must be <= 0.0 dBFS
            let max_possible_output = result.max_boost_db + result.preamp_headroom_db;
            assert!(max_possible_output <= 0.0);
        }
    }

    #[test]
    fn test_output_buffer_layout() {
        let mut buffer = [0.0f32; 256];
        let result = compute_dsp_parameters(48000.0, true, 1.0, 1.0);
        write_dsp_result_to_slice(&result, &mut buffer);

        // Pre-EQ bands (0..31 freqs, 32..63 gains)
        assert_eq!(buffer[0], 20.0);
        assert!(buffer[31] > 500.0 && buffer[31] < 700.0);

        // Post-EQ bands (64..95 freqs, 96..127 gains)
        assert!(buffer[64] > 600.0);
        assert!((buffer[95] - 20000.0).abs() < 10.0);

        // Index 128 is preamp headroom
        assert_eq!(buffer[128], result.preamp_headroom_db);

        // Index 129 is max boost
        assert_eq!(buffer[129], result.max_boost_db);
    }

    #[test]
    fn test_nyquist_clamping_safety() {
        // Extremely high frequency near Nyquist or above
        let filter = BiquadFilter::peaking_eq(30000.0, 0.7071, 3.0, 44100.0);
        let mag = filter.magnitude_db(20000.0, 44100.0);
        assert!(mag.is_finite());
        assert!(!mag.is_nan());

        // Low sample rate
        let filter_low_sr = BiquadFilter::low_shelf(50.0, 0.7071, 3.0, 8000.0);
        let mag_low_sr = filter_low_sr.magnitude_db(50.0, 8000.0);
        assert!(mag_low_sr.is_finite());
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

        // Invalid buffer
        let null_status = unsafe {
            zenith_dsp_compute(48000.0, 1, 1.0, 1.0, std::ptr::null_mut(), 0)
        };
        assert_eq!(null_status, -1);
    }
}
