package io.github.probecat.airplanescheduler;

interface IAirplaneService {
    void destroy() = 16777114;
    // Returns the changes that worked, as ConnectivityPlan bits. A new code, so a service left
    // from an older version fails instead of reading the arguments wrong.
    int setEnabled(boolean enabled, boolean airplane, boolean wifi) = 2;
}
