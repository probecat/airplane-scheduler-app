package io.github.probecat.airplanescheduler;

interface IAirplaneService {
    void destroy() = 16777114;
    // Returns the changes that worked, as ConnectivityPlan bits. The transaction code is new, so a service
    // that an older version left running fails instead of reading the arguments wrong.
    int setEnabled(boolean enabled, boolean airplane, boolean wifi) = 2;
}
