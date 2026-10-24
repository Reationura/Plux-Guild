package com.guild.guild;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GuildBank
{
    private final AtomicLong balance = new AtomicLong(0L);
    private final Map<String, Map<Long, Long>> depositHistory = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Long>> withdrawHistory = new ConcurrentHashMap<>();

    // 3.0.8：限制每人历史记录条数，防止长期运行内存无限增长
    // 3.0.9：改为实例字段，接入 bank.yml max-history-records（旧版该配置为预留项不生效）
    private static final int DEFAULT_MAX_RECORDS_PER_PLAYER = 50;
    private volatile int maxRecordsPerPlayer = DEFAULT_MAX_RECORDS_PER_PLAYER;

    /** 设置每人保留的历史记录条数（<=0 时回退默认 50） */
    public void setMaxRecordsPerPlayer(int maxRecords)
    {
        this.maxRecordsPerPlayer = maxRecords > 0 ? maxRecords : DEFAULT_MAX_RECORDS_PER_PLAYER;
    }

    public int getMaxRecordsPerPlayer()
    {
        return maxRecordsPerPlayer;
    }

    public long getBalance()
    {
        return balance.get();
    }

    public void setBalance(long amount)
    {
        balance.set(amount);
    }

    public boolean deposit(long amount)
    {
        if (amount <= 0L) return false;
        balance.addAndGet(amount);
        return true;
    }

    public boolean withdraw(long amount)
    {
        if (amount <= 0L) return false;
        while (true)
        {
            long current = balance.get();
            if (current < amount) return false;
            if (balance.compareAndSet(current, current - amount)) return true;
        }
    }

    public void addDepositRecord(String playerName, long amount)
    {
        Map<Long, Long> records = depositHistory.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>());
        records.put(System.currentTimeMillis(), amount);
        trimHistory(records);
    }

    public void addWithdrawRecord(String playerName, long amount)
    {
        Map<Long, Long> records = withdrawHistory.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>());
        records.put(System.currentTimeMillis(), amount);
        trimHistory(records);
    }

    /** 仅保留最近 maxRecordsPerPlayer 条记录，丢弃最早的时间戳条目 */
    private void trimHistory(Map<Long, Long> records)
    {
        int limit = maxRecordsPerPlayer;
        if (records.size() <= limit) return;
        // 快照 key 避免并发修改异常，key 即时间戳，升序丢弃最早的条目
        ArrayList<Long> timestamps = new ArrayList<>(records.keySet());
        Collections.sort(timestamps);
        int toRemove = timestamps.size() - limit;
        for (int i = 0; i < toRemove; i++)
        {
            records.remove(timestamps.get(i));
        }
    }

    public Map<String, Map<Long, Long>> getDepositHistory()
    {
        Map<String, Map<Long, Long>> result = new ConcurrentHashMap<>();
        depositHistory.forEach((name, records) ->
            result.put(name, new ConcurrentHashMap<>(records)));
        return result;
    }

    public Map<String, Map<Long, Long>> getWithdrawHistory()
    {
        Map<String, Map<Long, Long>> result = new ConcurrentHashMap<>();
        withdrawHistory.forEach((name, records) ->
            result.put(name, new ConcurrentHashMap<>(records)));
        return result;
    }

    public void clearHistory()
    {
        depositHistory.clear();
        withdrawHistory.clear();
    }
}
