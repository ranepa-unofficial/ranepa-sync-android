package com.qteb.ranepa.sync.system

/** Видно ли приложение на экране: уведомления тогда не всплывают поверх него. */
object AppVisibility {
    @Volatile var foreground = false
}
