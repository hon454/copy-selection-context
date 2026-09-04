package demo

val timeout = 10
val retries = 3

fun describePolicy(): String = "timeout=$timeout, retries=$retries"
