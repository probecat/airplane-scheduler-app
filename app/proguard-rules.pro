# reason: Shizuku starts AirplaneService in its own process.
-keep class io.github.probecat.airplanescheduler.system.AirplaneService { <init>(); }
