package com.example.foregroundservice

class Position(width : Int,height : Int) {
    var nwidth = width
    var nheight = height
}
class Position1(width : Int,height : Int,widthn : Int,heightn : Int, new_upi : Boolean) {
    var nwidth = width
    var nheight = height
    var nwidth2 = widthn
    var nheight2 = heightn
    var nnew_upi = new_upi
}
class Position3(sstring1 : String,sstring2 : String) {
    var nsstring1 = sstring1
    var nsstring2 = sstring2
}
class Position4(bool : Boolean,sstring2 : String) {
    var nsstring1 = bool
    var nsstring2 = sstring2
}
data class Contact(var name: String = "", var number: String = "")
