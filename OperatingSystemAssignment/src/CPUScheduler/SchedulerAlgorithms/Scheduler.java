package CPUScheduler.SchedulerAlgorithms;

import CPUScheduler.CPU.CPU;
import CPUScheduler.Configurations.GlobalUtilities;
import CPUScheduler.Dispatcher.Dispatcher;
import CPUScheduler.Exceptions.DirectoryGenerateFailedException;
import CPUScheduler.Exceptions.IllegalMethodCallException;
import CPUScheduler.Logger.Log;
import CPUScheduler.Process.ProcessObjects;

import java.util.*;

public abstract class Scheduler{
    //스케줄러 이름
    private static String SchedulerAlgorithmName;

    // 수행이 완료된 프로세스 저장
    protected LinkedList<ProcessObjects> finishedQueue =new LinkedList<>();
    // Genereated Processor List Stack
    // Set access modifier protected, Inherited class should be access this variable
    protected LinkedList<ProcessObjects> ProcessStack = new LinkedList<>();
    // Process Ready Queue : Ready state
    protected LinkedList<ProcessObjects> ReadyQueue = new LinkedList<>();
    // IO Stated Queue : Blocking state
    protected LinkedList<ProcessObjects> IOQueue = new LinkedList<>();

    // 스케줄러 전체 작동 시간을 의미한다. 여기서 스케줄러는 이 시뮬레이터 자체를 의미한다.
    protected int SchedulerTotalRunningTime = 0;
    //Total Process Count
    private final int processCount;
    // CPU Instance
    protected CPU cpu = CPU.getInstance();
    // Dispatcher Instance
    protected Dispatcher dispatcher = Dispatcher.getInstance();

    //CPU IDLE Time
    private int CPUIDLETime = 0;
    // I/O IDLE Time
    private int IOIDLETime = 0;

    //Abstract Class "Scheduler"'s Constructor
    //Initiate Process
    public Scheduler(int processCount,List<Integer> preprocessPCBs){
        Log.Logger("===========================");
        Log.Logger("[ Scheduler Simulator is now Running ]");
        initiateProcess(preprocessPCBs);
        this.processCount = processCount;
    }

    // Initiate Processes for this Scheduler
    private void initiateProcess(List<Integer> preprocessPCBs){
        int pid_counter = 1;
        for(int i = 0; i < preprocessPCBs.size();i += 4){
            /*
            System.out.println(Integer.toString(preprocessPCBs.get(i))+
                    Integer.toString(preprocessPCBs.get(i+1))
                    +Integer.toString(preprocessPCBs.get(i+2))
                    +Integer.toString(preprocessPCBs.get(i+3)));
             */
            ProcessStack.add(new ProcessObjects(
                    pid_counter
                    ,preprocessPCBs.get(i)
                    ,preprocessPCBs.get(i+1)
                    ,preprocessPCBs.get(i+2)
                    ,preprocessPCBs.get(i+3)));
            pid_counter++;
        }
    }

    public static String getSelectedAlgorithmName() throws IllegalMethodCallException {
        if(SchedulerAlgorithmName == null){
            throw new IllegalMethodCallException();
        }else{
            return SchedulerAlgorithmName;
        }
    }

    // Arrival Time을 기준으로 프로세스들을 정렬한다. ProcessObject의 compareTo를 구현해 놓음
    protected void SortProcessStackWithArrivalTime(){
        Collections.sort(ProcessStack);
    }

    protected boolean checkCPUReadyQueueEmpty(){
        return ReadyQueueEmpty() && !cpu.CPUhasProcess();
    }

    protected void ReEnqueueToReadyQueue(ProcessObjects processObjects){
        //프로세스 객체의 CPU Burst, IOBurst를 다시 랜덤값으로 초기화한다.
        processObjects.setArrivalTime(SchedulerTotalRunningTime);
        processObjects.setCPUBurstIOBurstRandom();
        // ReadyQueue가 비어있고, CPU에서 Running State인 프로세스가 존재하지 않는다면
        // ReadyQueue로 가지 않고 바로 CPU에 올린다.
        if(checkCPUReadyQueueEmpty()){
            cpu.setProcess(processObjects);
            // 위 두 조건중 하나라도 충족 안할 시 ReadyQueue로 보낸다.
        }else{
            ReadyQueue.add(processObjects);
        }
    }

    // Overloading ReEnqueToReadyQueue
    protected void ReEnqueueToReadyQueue(ProcessObjects processObjects,boolean ifCpuValuesNotChanged){
        // 만약 CPU 관련 value들이(CPU Time, CPU Burst) 변하지 않는다고 한다면,
        if(ifCpuValuesNotChanged){
            // 만약 ReadyQueue가 비어있고, CPU에서 작동중인 프로세스가 없으면 바로 CPU에 넣어준다.
            if(checkCPUReadyQueueEmpty()){
                Log.Logger("Process  : " + processObjects.getPid() + " re-set to Running State. Ready Queue & CPU Running Process is empty");
                cpu.setProcess(processObjects);
            }else{
                // 그대로 넣는다
                Log.Logger("Process " + processObjects.getPid() + " go back to ReadyQueue Directly.");
                Log.Logger("Process left CPU Time : " + processObjects.getRemaining_cpu_time());
                Log.Logger("Process left CPU Burst Time : " + processObjects.getRemaining_cpu_burst());
                ReadyQueue.add(processObjects);
            }
        }
        // 변해야하는 경우에는 기존 ReEnqueueToReadyQueue()메소드를 그대로 사용
        else{
            ReEnqueueToReadyQueue(processObjects);
        }
    }

    protected void EnqueToIOQueue(ProcessObjects processObjects){
        //프로세스의 CPU Time이 0이된 순간에는 더이상 IO가 발생하지 않는다. 그렇기 때문에, 바로 Finish Queue로 넣어준다.
        if(processObjects.getRemaining_cpu_time() <= 0){
            processObjects.setFinishedTime(SchedulerTotalRunningTime);
            addToFinishedQueue(processObjects);
        }
        // CPU Time은 0보다 크다
        // 만약 프로세스의 전체 IO Burst가 0보다 작거나 같으면 IO Queue로 안들어 가고 ReadyQueue로 가게 된다
        // IO Burst가 0이하인 프로세스에 대해서
        else if(processObjects.getIOBurstTime() <= 0){
            ReEnqueueToReadyQueue(processObjects);
        }
        // 만약 아닌 경우, IOQueue로 들어가게 된다.
        else{
            Log.Logger("Process : " + processObjects.getPid() + " go to I/O State(Blocked State)");
            IOQueue.add(processObjects);
        }
    }


    // print selected algorithm and Processes that initialized
    protected void PrintAlgorithmNameAndProcesses(String AlgorithmName){
        SchedulerAlgorithmName = AlgorithmName;
        Log.Logger("[ Selected Scheduling Algorithm : " + AlgorithmName + " Algorithm ]");
        Log.Logger("[ Process initiated. Process initiated list will be show below ]");
        printProcessList();
        Log.Logger("===========================");
        Log.Logger("Simulation will start in 5second");
        //FixedVariables.BreakConsole(5000);
    }

    // Overloading
    // additional print : Time Quantum for Rounded Robin
    protected void PrintAlgorithmNameAndProcesses(String AlgorithmName,int TimeQuantum){
        SchedulerAlgorithmName = AlgorithmName;
        Log.Logger("[ Selected Scheduling Algorithm : " + AlgorithmName + " Algorithm ]");
        Log.Logger("[ Time Quantum : " + TimeQuantum + " ]");
        Log.Logger("[ Process initiated. Process initiated list will be show below ]");
        printProcessList();
        Log.Logger("===========================");
        Log.Logger("Simulation will start in 5second");
        //FixedVariables.BreakConsole(5000);
    }

    protected void addToFinishedQueue(ProcessObjects process){
        Log.Logger("Process " + process.getPid() + " end of running and enque to finished Queue at " + SchedulerTotalRunningTime);
        finishedQueue.add(process);
    }

    //각 순환별 최초로 실행해야하는 작업들이다.
    protected void IntegratedInitialJobPerEachCircular(){
        CheckSchedulerExitCondition(); // 종료조건 검사
        CheckProcessStackAndEnqueToReadyQueue(); // 프로세스 스택에서 각 프로세스들의 ArrivalTime을 검사한 후에 ReadyQueue에 넣는작업
        CheckIOQueueUnLockBlockedState(); // IO Queue에서 프로세스의 IO Burst를 검사한다.
    }

    // 각 순환별 마지막으로 실행해 주어야 하는 작업들이다.
    // 이유 : 초반 실행히 CPU의 프로세스가 null인 상태에서 연산을 하게되면 NullPointerException 예외의 위험성으로 인해
    protected void IntegratedAfterJobPerEachCircular(){
        printCycleSummary();
        CheckSchedulerExitCondition(); // 종료조건 검사 : 사이클이 끝나기 전에도 검사를 해주어야 한다. 이 과정이 없으면 뒤에 불필요한 연산이 생기므로
        ReadyQueueAddOneSecond(); // Ready Queue의 프로세스 ready state 1초씩 증가
        IOQueueAddOneSecond(); // IO Queue의 프로세스들 blocked state 1초씩 증가
        cpu.processOneSecondPast(); // CPU의 주도권을 가지고 있는 프로세스의
        IOIDLEOneSecond(); // IO IDLE상태에 대한 검사
        CPUIDLEOneSecond(); // CPU IDLE상태에 대한 검사
        SchedulerTotalRunningTime++;
        Log.Logger("Cycle ends\n");
    }

    protected void printCycleSummary(){
        Log.Logger("\n** Cycle Summary **");
        ArrayList<String> e = new ArrayList<>();
        Log.Logger("( CPU Info )");
        Log.Logger("Running Process : ",false);
        if(cpu.CPUhasProcess()){
            Log.Logger(cpu.getProcess().getPid());
        }else{
            Log.Logger("No other process Running");
        }
        Log.Logger("( Ready Queue )");
        Log.Logger("Ready Queue : ",false);
        if(ReadyQueueEmpty()){
            Log.Logger("No other process is ready state");
        }else{
            for(ProcessObjects p : ReadyQueue){
                e.add(p.getPid());
            }
            Log.Logger(String.join("->",e));
        }
        e.clear();
        Log.Logger("( I/O Queue )");
        Log.Logger("I/O Queue : ",false);
        if(IOQueueEmpty()){
            Log.Logger("No other process is blocked state");
        }else{
            for(ProcessObjects p : IOQueue){
                //Debugging Comment
                //System.out.println(p.getRemaining_cpu_time() + " " + p.getRemaining_cpu_burst() + " " + p.getRemaining_io_burst());
                e.add(p.getPid());
            }
            Log.Logger(String.join("->",e));
        }
        Log.Logger("*******************\n");
    }

    protected void printProcessList(){
        String msg = "";
        for(ProcessObjects p : ProcessStack){
            msg += p;
        }
        Log.Logger(msg);
    }

    // Ready Queue에 있는 모든 프로세스에 대해 ready state값을 1씩 증가시켜준다
    protected void ReadyQueueAddOneSecond(){
        for(ProcessObjects p : ReadyQueue){
            p.oneSecondPastReadyQueue();
        }
    }
    // IO Queue에 있는 모든 프로세스에 대해 blocked state값을 1씩 증가시키고 remaining io burst를 1감소시킨다.
    protected void IOQueueAddOneSecond(){
        for(ProcessObjects p : IOQueue){
            p.oneSecondPastIOQueue();
        }
    }

    //스케줄러의 실행시간에 1초를 더해준다.
    protected void SchedulerRunningTimeAddOneSecond(){
        SchedulerTotalRunningTime++;
    }

    protected void CPUIDLEOneSecond(){
        // CPU에 프로세스가 없을때만 IDLE Time에 1초를 더해준다
        if(!cpu.CPUhasProcess()){
            CPUIDLETime++;
        }
    }

    protected void IOIDLEOneSecond(){
        // IO Queue에서 I/O상태인 프로세스가 없을때만 1초를 더해준다.
        if(IOQueue.isEmpty()){
            IOIDLETime++;
        }
    }

    //ProcessStack에서 현재 스케줄러 러닝 타임에 비해 작거나 같은 프로세스가 있으면 Ready Queue에 Enque한다
    // 스케줄러 최초 실행해 ProcessStack은 ArrivalTime을 기준으로 정렬되기 때문에 이렇게 해도 문제가 되지 않음
    protected void CheckProcessStackAndEnqueToReadyQueue(){
        Log.Logger("Checking Process Arrival Time");
        int counter = 0;
        for(ProcessObjects p : ProcessStack){
            if(p.getArrivalTime() <= SchedulerTotalRunningTime){
                counter++;
            }
        }
        for(int i = 0; i < counter;i++){
            ReadyQueue.add(ProcessStack.removeFirst());
        }
    }

    // IOQueue에서 Remaining IO Burst Time이 0보다 작은경우에 대해 처리한다
    protected void CheckIOQueueUnLockBlockedState(){
        Queue<ProcessObjects> q = new LinkedList<>();
        // 우선 IO Queue에서 IOBurst가 0보다 작은 Process가 있는지 확인한다. 있는 경우 q에 저장
        for (ProcessObjects processObjects : IOQueue) {
            if (processObjects.getRemaining_io_burst() <= 0) {
                q.add(processObjects);
            }
        }
        //I/O작업이 끝난 프로세스들에 대해서
        for(ProcessObjects processObjects : q){
            // CPU Time이 0 이하인 경우 -> 종료된 프로세스
            if(processObjects.getRemaining_cpu_time() <= 0){
                Log.Logger(processObjects.getPid());
                processObjects.setFinishedTime(SchedulerTotalRunningTime);
                addToFinishedQueue(processObjects);
                // CPU Time이 1이상 -> 아직 작동중인 프로세스
            }else{
                ReEnqueueToReadyQueue(processObjects);
            }
            IOQueue.remove(processObjects);
        }
    }

   // ReadyQueue비어있는지 확인한다
    protected boolean ReadyQueueEmpty(){
        return ReadyQueue.isEmpty();
    }
    //IOQueue비어있는지 확인한다
    protected boolean IOQueueEmpty(){
        return IOQueue.isEmpty();
    }
    //ProcessStack비어있는지 확인한다
    protected boolean ProcessStackEmpty(){
        return ProcessStack.isEmpty();
    }

    protected void CheckSchedulerExitCondition(){
        Log.Logger("Check Scheduler Exit Condition");
        /*
        * 종료조건
        * 1. ProcessStack에 남은 스택 없음
        * 2. ReadyQueue가 비어있음
        * 3. IOQueue가 비어있음
        * 4. CPU에 돌아가고 있는 프로세스가 없을때
        * */
        if(ProcessStackEmpty() && ReadyQueueEmpty() && IOQueueEmpty() && !cpu.CPUhasProcess()){
            try{
                Log.Logger("Scheduler Simulation End!");
                // Statement Deprecated : Version 2.0.0
                //SchedulerTotalRunningTime = finishedQueue.getLast().getFinishedTime();
                printSummary();
                Log.SaveLogAsTxt();
                GlobalUtilities.ExitProgram();
            }catch (DirectoryGenerateFailedException e){
                GlobalUtilities.ExitProgram();
            }
        }
    }

    // 분기줄 계산메소드
    protected String CalculateLines(String msg){
        int max = 50;
        int size = max - msg.length();
        size = size / 2;
        String line = "";
        for(int i = 0; i < size;i++){
            line += "=";
        }
        return "\n" + line + " " + msg + " " + line;
    }

    protected void printSummary(){
        int totalRunningOfProcesses = 0;
        int totalBlockedOfProcesses = 0;
        int totalTurnAroundTimeProcesses = 0;
        int totalWaitingTimeProcesses = 0;


        Log.Logger(CalculateLines("[ Summary of " + SchedulerAlgorithmName + " finished order ]"));
        // 종료한 순서대로 PID 출력
        Queue<String> FinishQueuePID = new LinkedList<>();
        for(ProcessObjects p : finishedQueue){
            FinishQueuePID.add(p.getPid());
        }
        Log.Logger(String.join(" -> ",FinishQueuePID));
        Log.Logger(CalculateLines("[ Summary of each process ]"));

        for(ProcessObjects p : finishedQueue){
            Log.Logger("< Process : " + p.getPid() + " >");
            Log.Logger("Finishing Time : " + p.getFinishedTime());
            Log.Logger("Turnaround Time : " + (p.getFinishedTime() - p.getFirstArrivedTime()));
            totalTurnAroundTimeProcesses += (p.getFinishedTime() - p.getFirstArrivedTime());
            Log.Logger("CPU Time(Running State Time) : " + p.getTotal_running_time());
            totalRunningOfProcesses += p.getTotal_running_time();
            Log.Logger("I/O Time(Blocked State Time) : " + p.getTotal_blocked_time());
            totalBlockedOfProcesses += p.getTotal_blocked_time();
            Log.Logger("Waiting Time(Ready State Time) : " + p.getTotal_ready_time());
            totalWaitingTimeProcesses += p.getTotal_ready_time();
            Log.Logger("\n");
        }

        Log.Logger(CalculateLines("[ Summary of Scheduler ]"));
        Log.Logger("Scheduler Finishing Time : " + SchedulerTotalRunningTime);
        Log.Logger("Average turnaround time : " + String.format("%.2f",((float)totalTurnAroundTimeProcesses / (float) processCount)));
        Log.Logger("Average waiting time : " + String.format("%.2f",((float)totalWaitingTimeProcesses / (float) processCount)));
        Log.Logger("CPU Utilization : " + returnCPUUtilization());
        Log.Logger("I/O Utilization : " + returnIOUtilization());
        Log.Logger("Throughput in processes completed per hundred time units : " + returnThroughPutInProcessCompletedPerHundredTimeUnit());
    }

    // CPU Utilization
    protected String returnCPUUtilization(){
        float t = ((float)(SchedulerTotalRunningTime - CPUIDLETime) / (float)SchedulerTotalRunningTime) * 100;
        // 소수점 두자리까지만
        String value = String.format("%.2f",t);
        return value + " %";
    }

    // IO Utilization
    protected String returnIOUtilization(){
        float t = ((float)(SchedulerTotalRunningTime - IOIDLETime) / (float)SchedulerTotalRunningTime) * 100;
        // 소수점 두자리까지만
        String value = String.format("%.2f",t);
        return value + " %";
    }

    protected String returnThroughPutInProcessCompletedPerHundredTimeUnit(){
        float t = ((float)(finishedQueue.size())/(float)SchedulerTotalRunningTime) * 100;
        String value = String.format("%.2f",t);
        return value + " %";
    }


    // 스케줄링 알고리즘마다 다음 프로세스 선택기준이 다를 수 있으므로 추상메소드 정의
    public abstract ProcessObjects selectNextProcess();
    // Implement each cpu scheduling algorithm via override this method
    public abstract void Algorithm();
}
