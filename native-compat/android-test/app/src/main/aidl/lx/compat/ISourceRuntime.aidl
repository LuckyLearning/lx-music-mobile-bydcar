package lx.compat;

interface ISourceRuntime {
    int eval(int generation, String source, int memoryBytes, int cpuMs);
    String report(int generation, String source, int memoryBytes, int cpuMs);
    int pid();
    boolean privateFilesReadable();
    int generation();
    void bumpGeneration();
    void selfKill();
    int droppedLate();
}
