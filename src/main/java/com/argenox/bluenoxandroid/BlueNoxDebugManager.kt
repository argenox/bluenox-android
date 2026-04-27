/*
 * Copyright (c) 2017-2019, Argenox Technologies LLC
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
 * File:    DebugManager.java
 * Summary: Debug Manager
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class DebugManager

//private constructor.
private constructor() {
    private val _mutex: Lock = ReentrantLock(true)

    var log: String = ""
        private set

    fun setContext(ctx: Context?) {
    }

    fun logError(module: String, msg: String) {
        _mutex.lock()

        val timestamp = SimpleDateFormat("HH:mm:ss.sss", Locale.US).format(Date())

        log += "$timestamp  $module  $msg\n"

        _mutex.unlock()
    }

    fun logDebug(module: String, msg: String) {
        _mutex.lock()

        val timestamp = SimpleDateFormat("HH:mm:ss.sss", Locale.US).format(Date())

        log += "$timestamp  $module  $msg\n"

        _mutex.unlock()
    }

    private fun logFileName(): String {
        var fileName = ""
        val timestamp = SimpleDateFormat("MM_dd_yyyy_HH_mm_ss", Locale.US).format(Date())

        fileName += "log_$timestamp"
        fileName += ".log"
        return fileName
    }

    var logName: String? = null
        private set
    var logPath: String? = null
        private set


    fun createLogFile(): Boolean {
        try {
            logName = logFileName()

            val file = File(Environment.getExternalStorageDirectory(), "Argenox/" + logName)

            logPath = file.absolutePath
            try {
                if (!file.exists()) {
                    // file does not exist, create it
                    if (!file.createNewFile()) {
                        Log.d("DebugMgr", "Could not create new file")
                    }
                }

                val fileOutput = FileOutputStream(file)
                val outputStreamWriter = OutputStreamWriter(fileOutput)

                _mutex.lock()
                outputStreamWriter.write(log)
                _mutex.unlock()

                outputStreamWriter.flush()
                outputStreamWriter.close()

                return true
            } catch (e: IOException) {
                Log.e("Exception", "File write failed: $e")
            }
        } catch (e: Exception) {
            Log.e("SensorController", "exception", e)
        }

        return false
    }

    companion object {
        val instance: DebugManager = DebugManager()
    }
}
