/**
 * Procedural audio for the pixel layer.
 *
 * Everything is synthesised with the Web Audio API — there are no audio files in
 * the repository. That keeps the bundle small, sidesteps sample licensing, and
 * lets the music respond to game state (the meeting loop tightens as patience
 * drops) without shipping multiple stems.
 *
 * Design constraints, in priority order:
 *  1. Sound is never the sole carrier of information. Every cue it plays has a
 *     visual counterpart, so a muted or deaf player loses nothing.
 *  2. It starts muted-quiet and is trivially silenced; learners may be running
 *     this in a shared lab.
 *  3. The AudioContext is only created after a user gesture, per browser policy.
 */

export type SfxName =
  | 'step'
  | 'enter'
  | 'confirm'
  | 'deny'
  | 'evidence'
  | 'unlock'
  | 'notify'
  | 'blip'
  | 'openPanel'
  | 'closePanel'

interface ToneSpec {
  type: OscillatorType
  /** Frequency envelope in Hz: [start, end]. */
  freq: [number, number]
  /** Seconds. */
  duration: number
  gain: number
  /** Optional delay before the tone starts, for two-note cues. */
  delay?: number
}

/** Each cue is one or two short tones — enough character without becoming chirpy. */
const SFX: Record<SfxName, ToneSpec[]> = {
  step: [{ type: 'square', freq: [110, 90], duration: 0.045, gain: 0.05 }],
  enter: [
    { type: 'square', freq: [440, 660], duration: 0.08, gain: 0.12 },
    { type: 'square', freq: [660, 880], duration: 0.1, gain: 0.1, delay: 0.07 },
  ],
  confirm: [
    { type: 'triangle', freq: [523, 784], duration: 0.09, gain: 0.14 },
    { type: 'triangle', freq: [784, 1046], duration: 0.14, gain: 0.11, delay: 0.08 },
  ],
  deny: [
    { type: 'sawtooth', freq: [220, 165], duration: 0.12, gain: 0.11 },
    { type: 'sawtooth', freq: [165, 110], duration: 0.18, gain: 0.09, delay: 0.1 },
  ],
  evidence: [
    { type: 'square', freq: [880, 1320], duration: 0.06, gain: 0.1 },
    { type: 'square', freq: [1320, 1760], duration: 0.08, gain: 0.07, delay: 0.05 },
  ],
  unlock: [
    { type: 'triangle', freq: [523, 523], duration: 0.1, gain: 0.13 },
    { type: 'triangle', freq: [659, 659], duration: 0.1, gain: 0.13, delay: 0.09 },
    { type: 'triangle', freq: [1046, 1046], duration: 0.24, gain: 0.13, delay: 0.18 },
  ],
  notify: [{ type: 'sine', freq: [880, 1174], duration: 0.16, gain: 0.1 }],
  blip: [{ type: 'square', freq: [1400, 1400], duration: 0.018, gain: 0.035 }],
  openPanel: [{ type: 'sine', freq: [330, 660], duration: 0.11, gain: 0.09 }],
  closePanel: [{ type: 'sine', freq: [660, 330], duration: 0.11, gain: 0.08 }],
}

/**
 * The hub theme: a calm four-bar loop in A minor. Values are semitone offsets
 * from A2; `null` is a rest. Two voices — a bass pulse and a sparse lead — keep
 * it unobtrusive enough to leave running while reading.
 */
const BASS_LINE: (number | null)[] = [
  0, null, 0, null, -4, null, -4, null,
  -7, null, -7, null, -5, null, -5, null,
]
const LEAD_LINE: (number | null)[] = [
  24, null, 27, 24, 31, null, 28, null,
  27, null, 24, 27, 19, null, null, null,
]

const A2 = 110

function semitone(base: number, offset: number): number {
  return base * Math.pow(2, offset / 12)
}

export interface AudioSettings {
  muted: boolean
  musicVolume: number
  sfxVolume: number
}

export const DEFAULT_AUDIO_SETTINGS: AudioSettings = {
  muted: false,
  // Deliberately quiet defaults — this runs in classrooms.
  musicVolume: 0.18,
  sfxVolume: 0.45,
}

export class AudioEngine {
  private ctx: AudioContext | null = null
  private master: GainNode | null = null
  private musicBus: GainNode | null = null
  private sfxBus: GainNode | null = null
  private schedulerId: number | null = null
  private nextNoteTime = 0
  private step = 0
  private musicRunning = false
  private settings: AudioSettings = { ...DEFAULT_AUDIO_SETTINGS }
  /** Tempo in seconds per sixteenth. Raised when the client is losing patience. */
  private stepDuration = 0.22

  get isReady(): boolean {
    return this.ctx !== null
  }

  /** Must be called from a user gesture handler. Safe to call repeatedly. */
  resume(): void {
    if (!this.ctx) {
      const Ctor: typeof AudioContext | undefined =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
      if (!Ctor) return
      this.ctx = new Ctor()
      this.master = this.ctx.createGain()
      this.musicBus = this.ctx.createGain()
      this.sfxBus = this.ctx.createGain()
      this.musicBus.connect(this.master)
      this.sfxBus.connect(this.master)
      this.master.connect(this.ctx.destination)
      this.applySettings()
    }
    if (this.ctx.state === 'suspended') void this.ctx.resume()
  }

  updateSettings(next: Partial<AudioSettings>): void {
    this.settings = { ...this.settings, ...next }
    this.applySettings()
  }

  getSettings(): AudioSettings {
    return { ...this.settings }
  }

  private applySettings(): void {
    if (!this.master || !this.musicBus || !this.sfxBus || !this.ctx) return
    const t = this.ctx.currentTime
    this.master.gain.setTargetAtTime(this.settings.muted ? 0 : 1, t, 0.02)
    this.musicBus.gain.setTargetAtTime(this.settings.musicVolume, t, 0.05)
    this.sfxBus.gain.setTargetAtTime(this.settings.sfxVolume, t, 0.02)
  }

  play(name: SfxName): void {
    if (!this.ctx || !this.sfxBus || this.settings.muted) return
    const now = this.ctx.currentTime
    for (const spec of SFX[name]) {
      this.tone(spec, now + (spec.delay ?? 0), this.sfxBus)
    }
  }

  private tone(spec: ToneSpec, at: number, destination: GainNode): void {
    if (!this.ctx) return
    const osc = this.ctx.createOscillator()
    const gain = this.ctx.createGain()
    osc.type = spec.type
    osc.frequency.setValueAtTime(spec.freq[0], at)
    if (spec.freq[1] !== spec.freq[0]) {
      osc.frequency.exponentialRampToValueAtTime(Math.max(1, spec.freq[1]), at + spec.duration)
    }
    // Short attack, exponential decay — the classic chip envelope.
    gain.gain.setValueAtTime(0.0001, at)
    gain.gain.exponentialRampToValueAtTime(spec.gain, at + 0.006)
    gain.gain.exponentialRampToValueAtTime(0.0001, at + spec.duration)
    osc.connect(gain)
    gain.connect(destination)
    osc.start(at)
    osc.stop(at + spec.duration + 0.02)
  }

  startMusic(): void {
    if (!this.ctx || this.musicRunning) return
    this.musicRunning = true
    this.nextNoteTime = this.ctx.currentTime + 0.1
    this.step = 0
    this.scheduler()
  }

  stopMusic(): void {
    this.musicRunning = false
    if (this.schedulerId !== null) {
      window.clearTimeout(this.schedulerId)
      this.schedulerId = null
    }
  }

  /**
   * Raises the tempo as the client's patience falls, so the room gets tenser
   * without anyone being told. 1 = calm, 0 = about to be shown the door.
   */
  setTension(calm: number): void {
    const clamped = Math.max(0, Math.min(1, calm))
    this.stepDuration = 0.16 + clamped * 0.08
  }

  /**
   * Look-ahead scheduler: queues notes slightly into the future on a timer so
   * playback stays sample-accurate even when the main thread stutters.
   */
  private scheduler = (): void => {
    if (!this.ctx || !this.musicBus || !this.musicRunning) return
    const lookahead = 0.2
    while (this.nextNoteTime < this.ctx.currentTime + lookahead) {
      const i = this.step % BASS_LINE.length
      const bass = BASS_LINE[i]
      const lead = LEAD_LINE[i]
      if (bass !== null) {
        this.tone(
          { type: 'triangle', freq: [semitone(A2, bass), semitone(A2, bass)], duration: 0.2, gain: 0.5 },
          this.nextNoteTime,
          this.musicBus
        )
      }
      if (lead !== null) {
        this.tone(
          { type: 'square', freq: [semitone(A2, lead), semitone(A2, lead)], duration: 0.16, gain: 0.16 },
          this.nextNoteTime,
          this.musicBus
        )
      }
      this.nextNoteTime += this.stepDuration
      this.step += 1
    }
    this.schedulerId = window.setTimeout(this.scheduler, 60)
  }

  dispose(): void {
    this.stopMusic()
    void this.ctx?.close()
    this.ctx = null
  }
}

/** Process-wide instance; the world and the HUD both need to make noise. */
export const audio = new AudioEngine()
