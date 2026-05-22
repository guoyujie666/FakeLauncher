
package com.guoyujie666.fakelauncher.presentation  // 放到合适的包下

import com.guoyujie666.fakelauncher.aidl.IUserService
import java.io.BufferedReader
import java.io.InputStreamReader

class UserService : IUserService.Stub() {

    override fun execLine(command: String?): String {
        command ?: return ""
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            reader.close()
            output
        } catch (e: Exception) {
            e.message ?: "Error executing command"
        }
    }
}