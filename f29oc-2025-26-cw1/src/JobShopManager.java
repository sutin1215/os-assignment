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
    
    // Store the scheduling mode
    private String schedulingMode;

    // Extrinsic Monitor components
    private final ReentrantLock lock;
    private final Map<String, Condition> machineConditions; // Changed from single Condition to a Map

    // Constructor
    public JobShopManager(String mode) {
        // FCFS = First Come First Served
        // SJF = Shortest Job First 
        this.schedulingMode = mode;
        this.availableMachines = new HashMap<>();
        this.pendingJobs = new LinkedList<>();
        
        // Initialize the lock and our map of conditions
        this.lock = new ReentrantLock();
        this.machineConditions = new HashMap<>();
    }
    
    @Override
    public void specifyJobs(List<Job> jobs) {
        lock.lock();
        try {
            pendingJobs.addAll(jobs);
            System.out.println("DEBUG: Added " + jobs.size() + " jobs. Total in queue: " + pendingJobs.size());
            
            // BUG IDENTIFIED: Using an 'if' statement only processes the very first job!
            // If we submit multiple jobs at once, the remaining ones just sit in the queue 
            // even if we have enough machines to process them too. Need to change this to a loop.
            
            // Use our new helper to get the right job (FCFS or SJF)
            Job nextJob = getNextJob();
            
            while (nextJob != null && canProcessJob(nextJob)) {
                pendingJobs.remove(nextJob); // Manually remove this specific job from the queue
                assignMachinesToJob(nextJob);
                System.out.println("DEBUG: Job " + nextJob.jobName + " has secured its machines!");
                wakeUpMachines(nextJob);
                
                // Check if we can process another one
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
            // 1. Register this machine as available in the HashMap
            availableMachines.put(type, availableMachines.getOrDefault(type, 0) + 1);
            
            // 2. Initialize the condition for this machine type if it doesn't exist yet
            machineConditions.putIfAbsent(type, lock.newCondition());
            
            // 3. Since a new machine just arrived, let's check if the first waiting job can now start
            // Changed from 'if' to 'while' here as well, just in case one machine arriving 
            // is the missing piece for multiple small jobs!
            // Use our new helper to get the right job (FCFS or SJF)
            Job nextJob = getNextJob();
            
            while (nextJob != null && canProcessJob(nextJob)) {
                pendingJobs.remove(nextJob); // Manually remove this specific job from the queue
                assignMachinesToJob(nextJob);
                System.out.println("DEBUG: Job " + nextJob.jobName + " has secured its machines!");
                wakeUpMachines(nextJob);
                
                // Check if we can process another one
                nextJob = getNextJob();
            }

            // 4. Block the thread on its specific condition variable
            try {
                machineConditions.get(type).await(); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); 
            }
            
            // 5. Return the exact string format required by the output specification
            return type + " " + ID + " machine proceeding";
            
        } finally {
            lock.unlock();
        }
    }   

    // Helper method to count how many machines of each type a job needs
    private Map<String, Integer> getRequiredMachines(Job job) {
        Map<String, Integer> required = new HashMap<>();
        
        // Accessing the public final fields directly
        for (Operation op : job.operations) {
            String type = op.machineType;
            required.put(type, required.getOrDefault(type, 0) + 1);
        }
        return required;
    }

    // Helper method to check if we have enough available machines for a job
    private boolean canProcessJob(Job job) {
        Map<String, Integer> required = getRequiredMachines(job);
        
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String type = entry.getKey();
            int needed = entry.getValue();
            
            // If we don't have the machine type at all, or we don't have enough, return false
            if (availableMachines.getOrDefault(type, 0) < needed) {
                return false;
            }
        }
        return true; // We have enough of everything!
    }

    // Helper method to deduct machines from our available pool when a job takes them
    private void assignMachinesToJob(Job job) {
        Map<String, Integer> required = getRequiredMachines(job);
        
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String type = entry.getKey();
            int needed = entry.getValue();
            
            // Deduct the needed amount from the available pool
            availableMachines.put(type, availableMachines.get(type) - needed);
        }
    }
    // Helper to wake up exactly the right number of specific machines
    private void wakeUpMachines(Job job) {
        Map<String, Integer> required = getRequiredMachines(job);
        
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String type = entry.getKey();
            int needed = entry.getValue();
            
            // Bug Fix: Make sure the condition actually exists before signaling
            Condition condition = machineConditions.get(type);
            if (condition != null) {
                for (int i = 0; i < needed; i++) {
                    condition.signal(); 
                }
            }
        }
    }

    // Helper method to calculate the total processing time of a job for SJF scheduling
    private int getTotalProcessingTime(Job job) {
        int totalTime = 0;
        for (Operation op : job.operations) {
            totalTime += op.processingTime;
        }
        return totalTime;
    }

    // Helper method to find which job should be processed next based on the mode
    private Job getNextJob() {
        if (pendingJobs.isEmpty()) {
            return null;
        }
        
        if ("FCFS".equals(schedulingMode)) {
            return pendingJobs.peek(); // FCFS just takes the first one in line
        } 
        
        // SJF mode: Manually loop through to find the shortest job
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
    
}