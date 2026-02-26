package org.organicprogramming.holons

/** Standard gRPC server runner utilities. */
object Serve {
    fun parseFlags(args: Array<String>): String {
        var i = 0
        while (i < args.size) {
            when {
                args[i] == "--listen" && i + 1 < args.size -> return args[i + 1]
                args[i] == "--port" && i + 1 < args.size -> return "tcp://:${args[i + 1]}"
            }
            i++
        }
        return Transport.DEFAULT_URI
    }
}
