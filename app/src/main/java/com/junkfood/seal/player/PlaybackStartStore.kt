package com.junkfood.seal.player

object PlaybackStartStore {
    private var request: PlaybackStartRequest? = null

    fun set(request: PlaybackStartRequest) {
        this.request = request
    }

    fun hasRequest(): Boolean = request != null

    fun consume(): PlaybackStartRequest? = request.also { request = null }
}
