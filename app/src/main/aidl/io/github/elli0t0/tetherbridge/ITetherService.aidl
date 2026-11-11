package io.github.elli0t0.tetherbridge;

// Stage 1 only: one method that proves the privileged process is alive and
// tells us which identity it runs as (root uid 0, shell uid 2000, other).
interface ITetherService {
    // AIDL rule: either ALL methods get an explicit id or NONE do.
    // getInfo() takes 1, destroy() keeps the Shizuku slot 16777114.
    String getInfo() = 1;

    // Shizuku "destroy" slot: the server calls this transaction id when the
    // service version changes. Declaring it here (16777114) is per the
    // Shizuku README; the service exits its process inside destroy().
    void destroy() = 16777114;
}
