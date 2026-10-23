package com.justcal.app.ai

import kotlinx.coroutines.sync.Semaphore

/** Shared across production, Playground and deletion. A busy runtime never queues a second engine. */
internal object ModelRuntimeLease { val slot = Semaphore(1) }
