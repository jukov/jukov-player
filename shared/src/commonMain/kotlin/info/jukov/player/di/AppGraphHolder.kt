package info.jukov.player.di

object AppGraphHolder {
    val graph: AppGraph by lazy(::createAppGraph)
}
