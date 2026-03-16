import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;

//Important: 
// 1. The only concurrent or thread-safe classes that you 
//    allowed to import for this class are the two shown below.
// 2. This class must deal with all exceptions locally, i.e. 
//    it's public methods must not 'throw' any exceptions to the caller
//    otherwise our compilation of your code will fail.

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class JobShopManager implements JobShopInterface {        
    
    // Queue to hold pending jobs
    private Queue<Job> pendingJobs;
    // Track available machines by type
    private Map<String, Integer> availableMachines;
    // Ticket System: Holds the job names assigned to specific machine types
    private Map<String, Queue<String>> releasedMachines; 
    // Store the scheduling mode
    private String schedulingMode;

    // Extrinsic Monitor components
    private final ReentrantLock lock;
    private final Map<String, Condition> machineConditions; 

    // Constructor
    public JobShopManager(String mode) {
        // FCFS = First Come First Served
        // SJF = Shortest Job First 
        this.schedulingMode = mode;
        this.availableMachines = new HashMap<>();
        this.releasedMachines = new HashMap<>(); 
        this.pendingJobs = new LinkedList<>();
        
        // Initialize the lock and our map of conditions
        this.lock = new ReentrantLock();
        this.machineConditions = new HashMap<>();
    }

    // --- Helper Methods ---
    
    private int getTotalProcessingTime(Job job) {
        int totalTime = 0;
        for (Operation op : job.operations) {
            totalTime += op.processingTime;
        }
        return totalTime;
    }

    private Map<String, Integer> getRequiredMachines(Job job) {
        Map<String, Integer> required = new HashMap<>();
        for (Operation op : job.operations) {
            String type = op.machineType;
            required.put(type, required.getOrDefault(type, 0) + 1);
        }
        return required;
    }

    private Job getNextJob() {
        if (pendingJobs.isEmpty()) return null;
        
        if ("FCFS".equals(schedulingMode)) {
            return pendingJobs.peek(); 
        } 
        
        Job shortestJob = null;
        int shortestTime = Integer.MAX_VALUE;
        for (Job job : pendingJobs) {
            int time = getTotalProcessingTime(job);
            if (time < shortestTime) {
                shortestTime = time;
                shortestJob = job;
            }
        }
        return shortestJob;
    }

    private boolean canProcessJob(Job job) {
        Map<String, Integer> required = getRequiredMachines(job);
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (availableMachines.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true; 
    }

    private void assignMachinesToJob(Job job) {
        Map<String, Integer> required = getRequiredMachines(job);
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String type = entry.getKey();
            int needed = entry.getValue();
            
            // Deduct from available pool
            availableMachines.put(type, availableMachines.get(type) - needed);
            
            // Add jobName to the released pool
            releasedMachines.putIfAbsent(type, new LinkedList<>());
            for (int i = 0; i < needed; i++) {
                releasedMachines.get(type).add(job.jobName);
            }
        }
    }

    private void wakeUpMachines(Job job) {
        Map<String, Integer> required = getRequiredMachines(job);
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String type = entry.getKey();
            int needed = entry.getValue();
            
            Condition condition = machineConditions.get(type);
            if (condition != null) {
                for (int i = 0; i < needed; i++) {
                    condition.signal(); 
                }
            }
        }
    }

    // --- Interface Methods ---

    @Override
    public void specifyJobs(List<Job> jobs) {
        lock.lock();
        try {
            //Your code here
            pendingJobs.addAll(jobs);
            
            Job nextJob = getNextJob();
            while (nextJob != null && canProcessJob(nextJob)) {
                pendingJobs.remove(nextJob); 
                assignMachinesToJob(nextJob);
                wakeUpMachines(nextJob);
                nextJob = getNextJob();
            }
        } finally {
            lock.unlock(); 
        }
    }

    @Override
    public String thisMachineAvailable(String type, int ID) {
        lock.lock();
        try {
            //Your code here
            availableMachines.put(type, availableMachines.getOrDefault(type, 0) + 1);
            machineConditions.putIfAbsent(type, lock.newCondition());
            
            Job nextJob = getNextJob();
            while (nextJob != null && canProcessJob(nextJob)) {
                pendingJobs.remove(nextJob);
                assignMachinesToJob(nextJob);
                wakeUpMachines(nextJob);
                nextJob = getNextJob();
            }

            // Wait until there is a jobName in the queue for this machine type
            while (!releasedMachines.containsKey(type) || releasedMachines.get(type).isEmpty()) {
                try {
                    machineConditions.get(type).await(); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); 
                }
            }
            
            // Consume the ticket by polling the jobName from the queue
            String assignedJobName = releasedMachines.get(type).poll();
            
            // Replaced default: return "your return string here";
            return assignedJobName;
            
        } finally {
            lock.unlock();
        }
    }   
}