package com.drdisagree.teledrive.core.crypto

class KeyUnavailableException(name: String) :
    IllegalStateException("Key '$name' cannot be unwrapped on this device")
