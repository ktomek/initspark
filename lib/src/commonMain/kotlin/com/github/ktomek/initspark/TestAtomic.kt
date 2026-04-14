package com.github.ktomek.initspark
import kotlin.concurrent.Volatile
class TestAtomic { @Volatile var ref: Map<String, String> = emptyMap() }
