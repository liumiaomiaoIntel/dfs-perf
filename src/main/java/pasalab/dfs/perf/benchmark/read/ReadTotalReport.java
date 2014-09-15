package pasalab.dfs.perf.benchmark.read;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pasalab.dfs.perf.basic.PerfTotalReport;
import pasalab.dfs.perf.basic.TaskConfiguration;
import pasalab.dfs.perf.conf.PerfConf;

/**
 * Total report for read test.
 */
public class ReadTotalReport extends PerfTotalReport {
  private String mFailedSlaves = "";
  private int mFailedTasks = 0;
  private long mId = Long.MAX_VALUE;
  private int mSlavesNum;
  private String mReadType;

  private List<String> mSlaves;
  private Map<String, Integer> mAvaliableCores;
  private List<Float[]> mReadThroughput;

  @Override
  public void initialFromTaskContexts(File[] taskContextFiles) throws IOException {
    mSlavesNum = taskContextFiles.length;
    mSlaves = new ArrayList<String>(mSlavesNum);
    mAvaliableCores = new HashMap<String, Integer>();
    mReadThroughput = new ArrayList<Float[]>(mSlavesNum);

    for (File taskContextFile : taskContextFiles) {
      ReadTaskContext taskContext = ReadTaskContext.loadFromFile(taskContextFile);
      mSlaves.add(taskContext.getId() + "@" + taskContext.getNodeName());
      mReadType = taskContext.getReadType();
      mAvaliableCores.put(taskContext.getNodeName(), taskContext.getCores());
      if (taskContext.getStartTimeMs() < mId) {
        mId = taskContext.getStartTimeMs();
      }
      if (!taskContext.getSuccess()) {
        mFailedTasks++;
        mFailedSlaves += taskContext.getId() + "@" + taskContext.getNodeName() + " ";
        mReadThroughput.add(new Float[0]);
        continue;
      }
      long[] bytes = taskContext.getReadBytes();
      long[] timeMs = taskContext.getThreadTimeMs();
      Float[] throughput = new Float[bytes.length];
      for (int i = 0; i < bytes.length; i++) {
        // now throughput is in MB/s
        throughput[i] = bytes[i] / 1024.0f / 1024.0f / (timeMs[i] / 1000.0f);
      }
      mReadThroughput.add(throughput);
    }
  }

  private String generateSlaveDetails(int slaveIndex) {
    StringBuffer sbSlaveDetail =
        new StringBuffer(mSlaves.get(slaveIndex) + "'s throughput(MB/s) for each threads:\n\t");
    for (float throughput : mReadThroughput.get(slaveIndex)) {
      sbSlaveDetail.append("[ " + throughput + " ]");
    }
    sbSlaveDetail.append("\n\n");
    return sbSlaveDetail.toString();
  }

  private String generateReadConf() {
    StringBuffer sbReadConf = new StringBuffer();
    TaskConfiguration taskConf = TaskConfiguration.get("Read", true);
    sbReadConf.append("pasalab.dfs.perf.dfs.address\t" + PerfConf.get().DFS_ADDRESS + "\n");
    sbReadConf.append("files.per.thread\t" + taskConf.getProperty("files.per.thread") + "\n");
    sbReadConf.append("grain.bytes\t" + taskConf.getProperty("grain.bytes") + "\n");
    sbReadConf.append("identical\t" + taskConf.getProperty("identical") + "\n");
    sbReadConf.append("mode\t" + taskConf.getProperty("mode") + "\n");
    sbReadConf.append("threads.num\t" + taskConf.getProperty("threads.num") + "\n");
    sbReadConf.append("READ_TYPE\t" + mReadType + "\n");
    return sbReadConf.toString();
  }

  private String generateSystemConf() {
    StringBuffer sbSystemConf = new StringBuffer("NodeName\tCores\n");
    int totalCores = 0;
    for (Map.Entry<String, Integer> nodeCores : mAvaliableCores.entrySet()) {
      String node = nodeCores.getKey();
      int core = nodeCores.getValue();
      totalCores += core;
      sbSystemConf.append(node + "\t" + core + "\n");
    }
    sbSystemConf.append("Total\t" + totalCores + "\n");
    return sbSystemConf.toString();
  }

  private String generateThroughput() {
    StringBuffer sbThroughput = new StringBuffer("SlaveName\tReadThroughput(MB/s)\n");
    float totalThroughput = 0;
    for (int i = 0; i < mSlavesNum; i++) {
      float slaveThroughput = 0;
      for (float throughput : mReadThroughput.get(i)) {
        slaveThroughput += throughput;
      }
      totalThroughput += slaveThroughput;
      sbThroughput.append(mSlaves.get(i) + "\t" + slaveThroughput + "\n");
    }
    sbThroughput.append("Total\t" + totalThroughput + "\n");
    return sbThroughput.toString();
  }

  @Override
  public void writeToFile(File file) throws IOException {
    BufferedWriter fout = new BufferedWriter(new FileWriter(file));
    fout.write(mTaskType + " Test - ID : " + mId + "\n");
    if (mFailedTasks == 0) {
      fout.write("Finished Successfully\n");
    } else {
      fout.write("Failed: " + mFailedTasks + " slaves failed ( " + mFailedSlaves + ")\n");
    }
    fout.write("********** System Configuratiom **********\n");
    fout.write(generateSystemConf());
    fout.write("********** Read Test Settings **********\n");
    fout.write(generateReadConf());
    fout.write("********** Read Throughput **********\n");
    fout.write(generateThroughput());
    fout.write("********** Slave Details **********\n");
    for (int i = 0; i < mSlavesNum; i++) {
      fout.write(generateSlaveDetails(i));
    }
    fout.close();
  }
}
