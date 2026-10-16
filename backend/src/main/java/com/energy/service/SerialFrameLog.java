package com.energy.service;

import com.energy.dto.FrameRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 串口原始报文环形缓冲（内存，最多保留 200 条），供前端"原始帧"面板排查问题。
 * 只记录协议匹配 / 端口识别等关键事务，不记录每 2 秒的常规采集，避免刷屏。
 */
@Component
public class SerialFrameLog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SIZE = 200;

    private final Deque<FrameRecord> records = new ConcurrentLinkedDeque<>();

    public void add(FrameRecord record) {
        if (record == null) {
            return;
        }
        record.setTime(LocalDateTime.now().format(FMT));
        records.addFirst(record);
        while (records.size() > MAX_SIZE) {
            records.pollLast();
        }
    }

    public List<FrameRecord> recent(int limit) {
        int n = limit <= 0 ? 50 : Math.min(limit, MAX_SIZE);
        List<FrameRecord> list = new ArrayList<>(n);
        for (FrameRecord r : records) {
            if (list.size() >= n) {
                break;
            }
            list.add(r);
        }
        return list;
    }
}
