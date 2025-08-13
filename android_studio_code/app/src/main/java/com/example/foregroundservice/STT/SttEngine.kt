package com.example.foregroundservice.STT

import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.speech.SpeechRecognizer
import androidx.lifecycle.MutableLiveData
import com.example.foregroundservice.NumberCallback

abstract class SttEngine {

    protected abstract var speechRecognizer: SpeechRecognizer?

    protected abstract var speechIntent: Intent

    protected abstract var audioManager: AudioManager

    protected abstract var restartSpeechHandler: Handler

    protected abstract var partialResultSpeechHandler: Handler

    protected abstract var listeningTime: Long

    protected abstract var pauseAndSpeakTime: Long

    protected abstract var finalSpeechResultFound: Boolean

    protected abstract var onReadyForSpeech: Boolean

    protected abstract var partialRestartActive: Boolean

    protected abstract var showProgressView: Boolean

    protected abstract var speechResult: MutableLiveData<String>

    protected abstract var speechFrequency: MutableLiveData<Float>
    protected abstract var startspeech: Boolean
    protected abstract var on_time: Long

    /**
     * Starts the speech recognition
     */
    abstract fun startSpeechRecognition(callback: NumberCallback)

    /**
     * Restarts the speech recognition
     * @param partialRestart The partial restart status
     */
    protected abstract fun restartSpeechRecognition(partialRestart: Boolean)

    /**
     * Closes the speech operations
     */
    abstract fun closeSpeechOperations()

    /**
     * Mutes the audio
     * @param mute Boolean The mute audio status
     */
    protected abstract fun mute(mute: Boolean)

    protected abstract var stop_time: Long
}