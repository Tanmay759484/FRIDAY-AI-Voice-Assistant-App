package com.example.foregroundservice.STT

interface SttListener {
    /**
     * Sends an update on the live speech result
     * @param liveSpeechResult String The live speech result
     */
    fun onSttLiveSpeechResult(liveSpeechResult: String)

    /**
     * Sends an update on the final speech result
     * @param speechResult String The final speech result
     */
    fun onSttFinalSpeechResult(speechResult: String)

    /**
     * Sends an update if there is an error
     * @param errMsg String The error message
     */
    fun onSttSpeechError(errMsg: String)
}