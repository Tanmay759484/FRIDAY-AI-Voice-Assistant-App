package com.example.foregroundservice

object SharedData {
    //var xco: Float = 605F
    //var yco: Float = 742F
    //var qco : Int = 1
    var xco: Float = 0F
    var yco: Float = 0F
    var qco : Boolean = false
    var touchPointDuration : Long = 0
    var is_database_delete : Boolean = false
    var back_button = false
    var back_button_state = false
    var stt_state = false
    var bluetooth_connection_state = false
    var speechResult = ""
    var mainActivity: MainActivity? = null
    var directory : String = ""
    var copy_text : String = ""
    var copy_text_stat : Boolean = false
    var saveDiscardDialog_stat = false
    var is_model_avl = false
    var touchByText = false
    var touchByText_contains = false
    var touchByText_text = ""
    var touchByText_nextView = ""
    var touchBynextView = false
    var get_recentList = false
    var get_recentList_list : List<String> = mutableListOf()
    var touchByText_noParent = false
    var pin : String = ""
    var enter_pin = false
    var accessibility_changed = false
    var input_accessi_activated = false
}
