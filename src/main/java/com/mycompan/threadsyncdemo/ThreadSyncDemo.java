package com.mycompan.threadsyncdemo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;
import java.util.List;

public class ThreadSyncDemo extends JFrame {
    private JComboBox<String> problemSelector;
    private JComboBox<String> syncMethodSelector;
    private JPanel visualPanel;
    private JTextArea logArea;
    private JButton startButton, stopButton, resetButton;
    private JLabel warningLabel;
    private volatile boolean running = false;
    private SynchronizationProblem currentProblem;

    public ThreadSyncDemo() {
        setTitle("Thread Synchronization Visual Demonstrator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Control Panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(new JLabel("Problem:"));
        problemSelector = new JComboBox<>(new String[]{
            "Producer-Consumer", "Dining Philosophers", "Readers-Writers"
        });
        controlPanel.add(problemSelector);

        controlPanel.add(new JLabel("Sync Method:"));
        syncMethodSelector = new JComboBox<>(new String[]{
            "Semaphores", "Monitors", "Unsafe (No Sync)"
        });
        controlPanel.add(syncMethodSelector);

        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        resetButton = new JButton("Reset");
        stopButton.setEnabled(false);

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(resetButton);

        add(controlPanel, BorderLayout.NORTH);

        // Warning Panel
        JPanel warningPanel = new JPanel();
        warningLabel = new JLabel("");
        warningLabel.setForeground(Color.RED);
        warningLabel.setFont(new Font("Arial", Font.BOLD, 14));
        warningPanel.add(warningLabel);
        add(warningPanel, BorderLayout.AFTER_LINE_ENDS);

        // Visual Panel
        visualPanel = new JPanel();
        visualPanel.setPreferredSize(new Dimension(800, 400));
        visualPanel.setBackground(Color.WHITE);
        visualPanel.setBorder(BorderFactory.createTitledBorder("Visualization"));
        add(visualPanel, BorderLayout.CENTER);

        // Log Area
        logArea = new JTextArea(10, 80);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Event Log"));
        add(scrollPane, BorderLayout.SOUTH);

        // Event Listeners
        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        resetButton.addActionListener(e -> resetSimulation());
        problemSelector.addActionListener(e -> resetSimulation());
        syncMethodSelector.addActionListener(e -> {
            resetSimulation();
            updateWarning();
        });

        pack();
        setLocationRelativeTo(null);
        resetSimulation();
    }

    private void updateWarning() {
        String syncMethod = (String) syncMethodSelector.getSelectedItem();
        if (syncMethod != null && syncMethod.equals("Unsafe (No Sync)")) {
            warningLabel.setText("⚠️ WARNING: Race conditions will occur!");
        } else {
            warningLabel.setText("");
        }
    }

    private void startSimulation() {
        running = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        problemSelector.setEnabled(false);
        syncMethodSelector.setEnabled(false);

        String problem = (String) problemSelector.getSelectedItem();
        String syncMethod = (String) syncMethodSelector.getSelectedItem();
        
        boolean useSemaphores = syncMethod.equals("Semaphores");
        boolean useMonitors = syncMethod.equals("Monitors");
        boolean unsafe = syncMethod.equals("Unsafe (No Sync)");

        switch (problem) {
            case "Producer-Consumer":
                currentProblem = new ProducerConsumerProblem(visualPanel, logArea, useSemaphores, unsafe);
                break;
            case "Dining Philosophers":
                currentProblem = new DiningPhilosophersProblem(visualPanel, logArea, useSemaphores, unsafe);
                break;
            case "Readers-Writers":
                currentProblem = new ReadersWritersProblem(visualPanel, logArea, useSemaphores, unsafe);
                break;
        }
        currentProblem.start();
    }

    private void stopSimulation() {
        running = false;
        if (currentProblem != null) {
            currentProblem.stop();
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void resetSimulation() {
        stopSimulation();
        logArea.setText("");
        visualPanel.removeAll();
        visualPanel.repaint();
        problemSelector.setEnabled(true);
        syncMethodSelector.setEnabled(true);
        updateWarning();
    }

    // Base class for synchronization problems
    abstract class SynchronizationProblem {
        protected JPanel panel;
        protected JTextArea log;
        protected boolean useSemaphores;
        protected boolean unsafe;
        protected List<Thread> threads = new ArrayList<>();
        protected volatile boolean running = true;

        public SynchronizationProblem(JPanel panel, JTextArea log, boolean useSemaphores, boolean unsafe) {
            this.panel = panel;
            this.log = log;
            this.useSemaphores = useSemaphores;
            this.unsafe = unsafe;
        }

        protected void logEvent(String message) {
            SwingUtilities.invokeLater(() -> {
                log.append(message + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            });
        }

        protected void logError(String message) {
            SwingUtilities.invokeLater(() -> {
                log.append("❌ ERROR: " + message + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            });
        }

        public abstract void start();

        public void stop() {
            running = false;
            for (Thread t : threads) {
                t.interrupt();
            }
        }
    }

    // Producer-Consumer Problem
    class ProducerConsumerProblem extends SynchronizationProblem {
        private List<Integer> buffer = Collections.synchronizedList(new ArrayList<>());
        private final int BUFFER_SIZE = 5;
        private Semaphore empty, full, mutex;
        private ReentrantLock lock;
        private Condition notFull, notEmpty;
        private JLabel[] bufferLabels;
        private JLabel[] producerLabels;
        private JLabel[] consumerLabels;

        public ProducerConsumerProblem(JPanel panel, JTextArea log, boolean useSemaphores, boolean unsafe) {
            super(panel, log, useSemaphores, unsafe);
            
            if (useSemaphores && !unsafe) {
                empty = new Semaphore(BUFFER_SIZE);
                full = new Semaphore(0);
                mutex = new Semaphore(1);
            } else if (!unsafe) {
                lock = new ReentrantLock();
                notFull = lock.newCondition();
                notEmpty = lock.newCondition();
            }
            setupUI();
        }

        private void setupUI() {
            panel.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);

            // Producers
            producerLabels = new JLabel[2];
            for (int i = 0; i < 2; i++) {
                producerLabels[i] = new JLabel("P" + i);
                producerLabels[i].setOpaque(true);
                producerLabels[i].setBackground(Color.GREEN);
                producerLabels[i].setPreferredSize(new Dimension(60, 60));
                producerLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                producerLabels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                gbc.gridx = i * 2;
                gbc.gridy = 0;
                panel.add(producerLabels[i], gbc);
            }

            // Buffer
            JPanel bufferPanel = new JPanel(new FlowLayout());
            bufferPanel.setBorder(BorderFactory.createTitledBorder("Buffer (Size: " + BUFFER_SIZE + ")"));
            bufferLabels = new JLabel[BUFFER_SIZE];
            for (int i = 0; i < BUFFER_SIZE; i++) {
                bufferLabels[i] = new JLabel("[ ]");
                bufferLabels[i].setPreferredSize(new Dimension(50, 50));
                bufferLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                bufferLabels[i].setBorder(BorderFactory.createLineBorder(Color.GRAY));
                bufferPanel.add(bufferLabels[i]);
            }
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = 4;
            panel.add(bufferPanel, gbc);

            // Consumers
            consumerLabels = new JLabel[2];
            for (int i = 0; i < 2; i++) {
                consumerLabels[i] = new JLabel("C" + i);
                consumerLabels[i].setOpaque(true);
                consumerLabels[i].setBackground(Color.BLUE);
                consumerLabels[i].setForeground(Color.WHITE);
                consumerLabels[i].setPreferredSize(new Dimension(60, 60));
                consumerLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                consumerLabels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                gbc.gridx = i * 2;
                gbc.gridy = 2;
                gbc.gridwidth = 1;
                panel.add(consumerLabels[i], gbc);
            }

            panel.revalidate();
            panel.repaint();
        }

        private void updateBufferDisplay() {
            SwingUtilities.invokeLater(() -> {
                int size = buffer.size();
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    if (i < size) {
                        bufferLabels[i].setText(String.valueOf(buffer.get(i)));
                        bufferLabels[i].setBackground(Color.YELLOW);
                    } else {
                        bufferLabels[i].setText("[ ]");
                        bufferLabels[i].setBackground(Color.WHITE);
                    }
                }
            });
        }

        private void updateThreadState(String threadName, String state, JLabel label) {
            SwingUtilities.invokeLater(() -> {
                if (state.contains("WAITING")) {
                    label.setBackground(Color.RED);
                } else if (state.contains("PRODUCING") || state.contains("CONSUMING")) {
                    label.setBackground(Color.ORANGE);
                } else {
                    label.setBackground(threadName.startsWith("P") ? Color.GREEN : Color.BLUE);
                    if (threadName.startsWith("C")) {
                        label.setForeground(Color.WHITE);
                    }
                }
                label.setToolTipText(state);
            });
        }

        public void start() {
            // Start Producers
            for (int i = 0; i < 2; i++) {
                final int id = i;
                Thread producer = new Thread(() -> {
                    int item = 0;
                    while (running) {
                        try {
                            if (unsafe) {
                                // UNSAFE: No synchronization
                                updateThreadState("P" + id, "CHECKING buffer", producerLabels[id]);
                                if (buffer.size() >= BUFFER_SIZE) {
                                    updateThreadState("P" + id, "WAITING (buffer full)", producerLabels[id]);
                                    Thread.sleep(100);
                                    continue;
                                }
                                
                                updateThreadState("P" + id, "PRODUCING item " + item, producerLabels[id]);
                                Thread.sleep(50); // Simulate work
                                buffer.add(item);
                                
                                if (buffer.size() > BUFFER_SIZE) {
                                    logError("Buffer overflow! Size: " + buffer.size() + " (Producer " + id + ")");
                                }
                                
                                logEvent("Producer " + id + " produced: " + item);
                                updateBufferDisplay();
                                item++;
                                
                            } else if (useSemaphores) {
                                updateThreadState("P" + id, "WAITING on empty semaphore", producerLabels[id]);
                                empty.acquire();
                                updateThreadState("P" + id, "WAITING on mutex", producerLabels[id]);
                                mutex.acquire();
                                
                                updateThreadState("P" + id, "PRODUCING item " + item, producerLabels[id]);
                                buffer.add(item);
                                logEvent("Producer " + id + " produced: " + item);
                                updateBufferDisplay();
                                item++;
                                
                                mutex.release();
                                full.release();
                                
                            } else {
                                lock.lock();
                                try {
                                    while (buffer.size() >= BUFFER_SIZE) {
                                        updateThreadState("P" + id, "WAITING on notFull condition", producerLabels[id]);
                                        notFull.await();
                                    }
                                    
                                    updateThreadState("P" + id, "PRODUCING item " + item, producerLabels[id]);
                                    buffer.add(item);
                                    logEvent("Producer " + id + " produced: " + item);
                                    updateBufferDisplay();
                                    item++;
                                    
                                    notEmpty.signal();
                                } finally {
                                    lock.unlock();
                                }
                            }

                            updateThreadState("P" + id, "RUNNING", producerLabels[id]);
                            Thread.sleep(1000 + (int)(Math.random() * 1000));
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Producer-" + id);
                threads.add(producer);
                producer.start();
            }

            // Start Consumers
            for (int i = 0; i < 2; i++) {
                final int id = i;
                Thread consumer = new Thread(() -> {
                    while (running) {
                        try {
                            Integer item = null;
                            
                            if (unsafe) {
                                // UNSAFE: No synchronization
                                updateThreadState("C" + id, "CHECKING buffer", consumerLabels[id]);
                                if (buffer.isEmpty()) {
                                    updateThreadState("C" + id, "WAITING (buffer empty)", consumerLabels[id]);
                                    Thread.sleep(100);
                                    continue;
                                }
                                
                                updateThreadState("C" + id, "CONSUMING", consumerLabels[id]);
                                Thread.sleep(50); // Simulate work
                                
                                try {
                                    item = buffer.remove(0);
                                } catch (IndexOutOfBoundsException e) {
                                    logError("Buffer underflow! (Consumer " + id + ")");
                                    continue;
                                }
                                
                            } else if (useSemaphores) {
                                updateThreadState("C" + id, "WAITING on full semaphore", consumerLabels[id]);
                                full.acquire();
                                updateThreadState("C" + id, "WAITING on mutex", consumerLabels[id]);
                                mutex.acquire();
                                
                                item = buffer.remove(0);
                                
                                mutex.release();
                                empty.release();
                                
                            } else {
                                lock.lock();
                                try {
                                    while (buffer.isEmpty()) {
                                        updateThreadState("C" + id, "WAITING on notEmpty condition", consumerLabels[id]);
                                        notEmpty.await();
                                    }
                                    
                                    item = buffer.remove(0);
                                    notFull.signal();
                                } finally {
                                    lock.unlock();
                                }
                            }

                            updateThreadState("C" + id, "CONSUMING item " + item, consumerLabels[id]);
                            logEvent("Consumer " + id + " consumed: " + item);
                            updateBufferDisplay();
                            updateThreadState("C" + id, "RUNNING", consumerLabels[id]);
                            Thread.sleep(1500 + (int)(Math.random() * 1000));
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Consumer-" + id);
                threads.add(consumer);
                consumer.start();
            }
        }
    }

    // Dining Philosophers Problem
    class DiningPhilosophersProblem extends SynchronizationProblem {
        private Semaphore[] forks;
        private ReentrantLock[] lockForks;
        private boolean[] forkTaken;
        private JLabel[] philosopherLabels;
        private JLabel[] forkLabels;

        public DiningPhilosophersProblem(JPanel panel, JTextArea log, boolean useSemaphores, boolean unsafe) {
            super(panel, log, useSemaphores, unsafe);
            
            forkTaken = new boolean[5];
            
            if (useSemaphores && !unsafe) {
                forks = new Semaphore[5];
                for (int i = 0; i < 5; i++) {
                    forks[i] = new Semaphore(1);
                }
            } else if (!unsafe) {
                lockForks = new ReentrantLock[5];
                for (int i = 0; i < 5; i++) {
                    lockForks[i] = new ReentrantLock();
                }
            }
            setupUI();
        }

        private void setupUI() {
            panel.setLayout(null);
            int centerX = 400;
            int centerY = 200;
            int radius = 120;

            philosopherLabels = new JLabel[5];
            forkLabels = new JLabel[5];

            for (int i = 0; i < 5; i++) {
                double angle = Math.toRadians(i * 72 - 90);
                int x = centerX + (int)(radius * Math.cos(angle)) - 30;
                int y = centerY + (int)(radius * Math.sin(angle)) - 30;

                philosopherLabels[i] = new JLabel("P" + i);
                philosopherLabels[i].setOpaque(true);
                philosopherLabels[i].setBackground(Color.CYAN);
                philosopherLabels[i].setPreferredSize(new Dimension(60, 60));
                philosopherLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                philosopherLabels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                philosopherLabels[i].setBounds(x, y, 60, 60);
                panel.add(philosopherLabels[i]);

                // Fork position
                double forkAngle = Math.toRadians(i * 72 + 36 - 90);
                int fx = centerX + (int)(radius * 0.6 * Math.cos(forkAngle)) - 15;
                int fy = centerY + (int)(radius * 0.6 * Math.sin(forkAngle)) - 15;

                forkLabels[i] = new JLabel("F" + i);
                forkLabels[i].setOpaque(true);
                forkLabels[i].setBackground(Color.LIGHT_GRAY);
                forkLabels[i].setPreferredSize(new Dimension(30, 30));
                forkLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                forkLabels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK));
                forkLabels[i].setBounds(fx, fy, 30, 30);
                panel.add(forkLabels[i]);
            }

            panel.revalidate();
            panel.repaint();
        }

        private void updatePhilosopherState(int id, String state) {
            SwingUtilities.invokeLater(() -> {
                if (state.contains("WAITING")) {
                    philosopherLabels[id].setBackground(Color.RED);
                } else if (state.contains("EATING")) {
                    philosopherLabels[id].setBackground(Color.GREEN);
                } else if (state.contains("THINKING")) {
                    philosopherLabels[id].setBackground(Color.CYAN);
                }
                philosopherLabels[id].setToolTipText(state);
            });
        }

        private void updateForkState(int id, boolean taken) {
            SwingUtilities.invokeLater(() -> {
                forkLabels[id].setBackground(taken ? Color.ORANGE : Color.LIGHT_GRAY);
            });
        }

        public void start() {
            for (int i = 0; i < 5; i++) {
                final int id = i;
                final int leftFork = id;
                final int rightFork = (id + 1) % 5;

                Thread philosopher = new Thread(() -> {
                    while (running) {
                        try {
                            // Thinking
                            updatePhilosopherState(id, "THINKING");
                            logEvent("Philosopher " + id + " is thinking");
                            Thread.sleep(1000 + (int)(Math.random() * 1000));

                            if (unsafe) {
                                // UNSAFE: No synchronization - deadlock and race conditions
                                updatePhilosopherState(id, "TRYING to pick forks");
                                
                                // Check and take left fork
                                if (forkTaken[leftFork]) {
                                    updatePhilosopherState(id, "WAITING on fork " + leftFork);
                                    Thread.sleep(100);
                                    continue;
                                }
                                Thread.sleep(50); // Simulate race window
                                forkTaken[leftFork] = true;
                                updateForkState(leftFork, true);
                                logEvent("Philosopher " + id + " picked up fork " + leftFork);
                                
                                // Check and take right fork
                                if (forkTaken[rightFork]) {
                                    logError("Philosopher " + id + " holds fork " + leftFork + " but can't get fork " + rightFork + " - POTENTIAL DEADLOCK!");
                                    updatePhilosopherState(id, "DEADLOCKED");
                                    Thread.sleep(500);
                                    forkTaken[leftFork] = false;
                                    updateForkState(leftFork, false);
                                    continue;
                                }
                                Thread.sleep(50); // Simulate race window
                                forkTaken[rightFork] = true;
                                updateForkState(rightFork, true);
                                logEvent("Philosopher " + id + " picked up fork " + rightFork);
                                
                                // Eating
                                updatePhilosopherState(id, "EATING");
                                logEvent("Philosopher " + id + " is eating");
                                Thread.sleep(1500 + (int)(Math.random() * 500));
                                
                                // Put down forks
                                forkTaken[leftFork] = false;
                                forkTaken[rightFork] = false;
                                updateForkState(leftFork, false);
                                updateForkState(rightFork, false);
                                logEvent("Philosopher " + id + " put down forks");
                                
                            } else {
                                // Prevent deadlock: odd philosophers pick right fork first
                                int first = (id % 2 == 0) ? leftFork : rightFork;
                                int second = (id % 2 == 0) ? rightFork : leftFork;

                                // Pick up first fork
                                updatePhilosopherState(id, "WAITING on fork " + first);
                                if (useSemaphores) {
                                    forks[first].acquire();
                                } else {
                                    lockForks[first].lock();
                                }
                                updateForkState(first, true);
                                logEvent("Philosopher " + id + " picked up fork " + first);

                                // Pick up second fork
                                updatePhilosopherState(id, "WAITING on fork " + second);
                                if (useSemaphores) {
                                    forks[second].acquire();
                                } else {
                                    lockForks[second].lock();
                                }
                                updateForkState(second, true);
                                logEvent("Philosopher " + id + " picked up fork " + second);

                                // Eating
                                updatePhilosopherState(id, "EATING");
                                logEvent("Philosopher " + id + " is eating");
                                Thread.sleep(1500 + (int)(Math.random() * 500));

                                // Put down forks
                                if (useSemaphores) {
                                    forks[first].release();
                                    forks[second].release();
                                } else {
                                    lockForks[first].unlock();
                                    lockForks[second].unlock();
                                }
                                updateForkState(first, false);
                                updateForkState(second, false);
                                logEvent("Philosopher " + id + " put down forks");
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Philosopher-" + id);
                threads.add(philosopher);
                philosopher.start();
            }
        }
    }

    // Readers-Writers Problem
    class ReadersWritersProblem extends SynchronizationProblem {
        private Semaphore resourceAccess, readCountAccess;
        private ReentrantReadWriteLock rwLock;
        private int readCount = 0;
        private boolean writerActive = false;
        private JLabel resourceLabel;
        private JLabel[] readerLabels;
        private JLabel[] writerLabels;

        public ReadersWritersProblem(JPanel panel, JTextArea log, boolean useSemaphores, boolean unsafe) {
            super(panel, log, useSemaphores, unsafe);
            
            if (useSemaphores && !unsafe) {
                resourceAccess = new Semaphore(1);
                readCountAccess = new Semaphore(1);
            } else if (!unsafe) {
                rwLock = new ReentrantReadWriteLock();
            }
            setupUI();
        }

        private void setupUI() {
            panel.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);

            // Readers
            readerLabels = new JLabel[3];
            for (int i = 0; i < 3; i++) {
                readerLabels[i] = new JLabel("R" + i);
                readerLabels[i].setOpaque(true);
                readerLabels[i].setBackground(Color.GREEN);
                readerLabels[i].setPreferredSize(new Dimension(60, 60));
                readerLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                readerLabels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                gbc.gridx = i;
                gbc.gridy = 0;
                panel.add(readerLabels[i], gbc);
            }

            // Shared Resource
            resourceLabel = new JLabel("SHARED RESOURCE");
            resourceLabel.setOpaque(true);
            resourceLabel.setBackground(Color.WHITE);
            resourceLabel.setPreferredSize(new Dimension(300, 100));
            resourceLabel.setHorizontalAlignment(SwingConstants.CENTER);
            resourceLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = 3;
            panel.add(resourceLabel, gbc);

            // Writers
            writerLabels = new JLabel[2];
            for (int i = 0; i < 2; i++) {
                writerLabels[i] = new JLabel("W" + i);
                writerLabels[i].setOpaque(true);
                writerLabels[i].setBackground(Color.BLUE);
                writerLabels[i].setForeground(Color.WHITE);
                writerLabels[i].setPreferredSize(new Dimension(60, 60));
                writerLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
                writerLabels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                gbc.gridx = i;
                gbc.gridy = 2;
                gbc.gridwidth = 1;
                panel.add(writerLabels[i], gbc);
            }

            panel.revalidate();
            panel.repaint();
        }

        private void updateResourceState(String state, Color color) {
            SwingUtilities.invokeLater(() -> {
                resourceLabel.setText(state);
                resourceLabel.setBackground(color);
            });
        }

        private void updateThreadState(String threadName, String state, JLabel label) {
            SwingUtilities.invokeLater(() -> {
                if (state.contains("WAITING")) {
                    label.setBackground(Color.RED);
                } else if (state.contains("READING")) {
                    label.setBackground(Color.ORANGE);
                } else if (state.contains("WRITING")) {
                    label.setBackground(Color.MAGENTA);
                } else {
                    label.setBackground(threadName.startsWith("R") ? Color.GREEN : Color.BLUE);
                    if (threadName.startsWith("W")) {
                        label.setForeground(Color.WHITE);
                    }
                }
                label.setToolTipText(state);
            });
        }

        public void start() {
            // Start Readers
            for (int i = 0; i < 3; i++) {
                final int id = i;
                Thread reader = new Thread(() -> {
                    while (running) {
                        try {
                            if (unsafe) {
                                // UNSAFE: No synchronization
                                updateThreadState("R" + id, "CHECKING resource", readerLabels[id]);
                                
                                if (writerActive) {
                                    logError("Reader " + id + " reading while writer active - DATA CORRUPTION!");
                                }
                                
                                readCount++;
                                updateThreadState("R" + id, "READING", readerLabels[id]);
                                updateResourceState("READING (Reader " + id + ")", Color.YELLOW);
                                logEvent("Reader " + id + " is reading");
                                Thread.sleep(1000 + (int)(Math.random() * 1000));
                                
                                readCount--;
                                updateThreadState("R" + id, "IDLE", readerLabels[id]);
                                if (readCount == 0) {
                                    updateResourceState("SHARED RESOURCE", Color.WHITE);
                                }
                                logEvent("Reader " + id + " finished reading");
                                
                            } else if (useSemaphores) {
                                updateThreadState("R" + id, "WAITING on readCountAccess", readerLabels[id]);
                                readCountAccess.acquire();
                                readCount++;
                                if (readCount == 1) {
                                    updateThreadState("R" + id, "WAITING on resourceAccess", readerLabels[id]);
                                    resourceAccess.acquire();
                                }
                                readCountAccess.release();

                                // Reading
                                updateThreadState("R" + id, "READING", readerLabels[id]);
                                updateResourceState("READING (Reader " + id + ")", Color.YELLOW);
                                logEvent("Reader " + id + " is reading");
                                Thread.sleep(1000 + (int)(Math.random() * 1000));

                                readCountAccess.acquire();
                                readCount--;
                                if (readCount == 0) {
                                    resourceAccess.release();
                                }
                                readCountAccess.release();

                                updateThreadState("R" + id, "IDLE", readerLabels[id]);
                                updateResourceState("SHARED RESOURCE", Color.WHITE);
                                logEvent("Reader " + id + " finished reading");
                                
                            } else {
                                updateThreadState("R" + id, "WAITING on read lock", readerLabels[id]);
                                rwLock.readLock().lock();

                                // Reading
                                updateThreadState("R" + id, "READING", readerLabels[id]);
                                updateResourceState("READING (Reader " + id + ")", Color.YELLOW);
                                logEvent("Reader " + id + " is reading");
                                Thread.sleep(1000 + (int)(Math.random() * 1000));

                                rwLock.readLock().unlock();
                                updateThreadState("R" + id, "IDLE", readerLabels[id]);
                                updateResourceState("SHARED RESOURCE", Color.WHITE);
                                logEvent("Reader " + id + " finished reading");
                            }

                            Thread.sleep(500 + (int)(Math.random() * 500));
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Reader-" + id);
                threads.add(reader);
                reader.start();
            }

            // Start Writers
            for (int i = 0; i < 2; i++) {
                final int id = i;
                Thread writer = new Thread(() -> {
                    while (running) {
                        try {
                            if (unsafe) {
                                // UNSAFE: No synchronization
                                updateThreadState("W" + id, "CHECKING resource", writerLabels[id]);
                                
                                if (writerActive) {
                                    logError("Multiple writers active - DATA CORRUPTION!");
                                }
                                if (readCount > 0) {
                                    logError("Writer " + id + " writing while " + readCount + " readers active - DATA CORRUPTION!");
                                }
                                
                                writerActive = true;
                                updateThreadState("W" + id, "WRITING", writerLabels[id]);
                                updateResourceState("WRITING (Writer " + id + ")", Color.PINK);
                                logEvent("Writer " + id + " is writing");
                                Thread.sleep(1500 + (int)(Math.random() * 1000));
                                
                                writerActive = false;
                                updateThreadState("W" + id, "IDLE", writerLabels[id]);
                                updateResourceState("SHARED RESOURCE", Color.WHITE);
                                logEvent("Writer " + id + " finished writing");
                                
                            } else if (useSemaphores) {
                                updateThreadState("W" + id, "WAITING on resourceAccess", writerLabels[id]);
                                resourceAccess.acquire();

                                // Writing
                                updateThreadState("W" + id, "WRITING", writerLabels[id]);
                                updateResourceState("WRITING (Writer " + id + ")", Color.PINK);
                                logEvent("Writer " + id + " is writing");
                                Thread.sleep(1500 + (int)(Math.random() * 1000));

                                resourceAccess.release();
                                updateThreadState("W" + id, "IDLE", writerLabels[id]);
                                updateResourceState("SHARED RESOURCE", Color.WHITE);
                                logEvent("Writer " + id + " finished writing");
                                
                            } else {
                                updateThreadState("W" + id, "WAITING on write lock", writerLabels[id]);
                                rwLock.writeLock().lock();

                                // Writing
                                updateThreadState("W" + id, "WRITING", writerLabels[id]);
                                updateResourceState("WRITING (Writer " + id + ")", Color.PINK);
                                logEvent("Writer " + id + " is writing");
                                Thread.sleep(1500 + (int)(Math.random() * 1000));

                                rwLock.writeLock().unlock();
                                updateThreadState("W" + id, "IDLE", writerLabels[id]);
                                updateResourceState("SHARED RESOURCE", Color.WHITE);
                                logEvent("Writer " + id + " finished writing");
                            }

                            Thread.sleep(1000 + (int)(Math.random() * 1500));
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Writer-" + id);
                threads.add(writer);
                writer.start();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ThreadSyncDemo().setVisible(true);
        });
    }
}