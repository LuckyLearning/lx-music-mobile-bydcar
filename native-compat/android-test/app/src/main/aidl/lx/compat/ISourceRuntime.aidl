package lx.compat;

import lx.compat.IScriptHost;

interface ISourceRuntime {
    void setHost(IScriptHost host);
    String command(String commandJson);
    int eval(int generation, String source, int memoryBytes, int cpuMs);
    String report(int generation, String source, int memoryBytes, int cpuMs);
    int pid();
    String loadedAbi();
    boolean privateFilesReadable();
    int generation();
    void bumpGeneration();
    void selfKill();
    int droppedLate();
}
