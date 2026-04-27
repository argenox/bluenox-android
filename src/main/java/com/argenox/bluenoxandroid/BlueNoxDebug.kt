/*
 * Copyright (c) 2015-2026, Argenox Technologies LLC
 *
 * Licensed under the terms in the LICENSE file at the module root.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ARGENOX TECHNOLOGIES LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * File:    BlueNoxDebug.kt
 * Summary: Debug logging and level control for BlueNox
 *
 **********************************************************************************/

package com.argenox.bluenoxandroid

import android.util.Log

class BlueNoxDebug(var lvl : DebugLevels)
{
    constructor() : this(DebugLevels.BLUENOX_DEBUG_LVL_ERROR) {
        setDebugLevel(lvl)    }

    /**
     * BlueNox Debug Levels
     *
     */
    public  enum class DebugLevels(val evt: Int) {
        BLUENOX_DEBUG_LVL_NONE(0),
        BLUENOX_DEBUG_LVL_ERROR(1),
        BLUENOX_DEBUG_LVL_WARNING(2),
        BLUENOX_DEBUG_LVL_DEBUG(3),
        BLUENOX_DEBUG_LVL_INFO(4),
        BLUENOX_DEBUG_LVL_ALL(5),
    }

    private var debugLvl : DebugLevels = DebugLevels.BLUENOX_DEBUG_LVL_NONE

    /**
     * Sets the Debug level for the Manager
     *
     */
    public fun setDebugLevel(lvl: DebugLevels) {
        debugLvl = lvl
    }

    /**
     * Prints Debug messages
     *
     */
    public fun debugPrint(lvl: DebugLevels, module: String, str: String, ) {

        if(debugLvl >= lvl) {
            Log.d(module, str)
        }
    }
}