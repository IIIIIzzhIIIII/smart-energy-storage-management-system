package com.energy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.energy.dto.DeviceDataStat;
import com.energy.entity.DeviceData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeviceDataMapper extends BaseMapper<DeviceData> {

    @Select("SELECT * FROM device_data WHERE device_id = #{deviceId} AND ts BETWEEN #{start} AND #{end} ORDER BY ts ASC LIMIT 2000")
    List<DeviceData> history(@Param("deviceId") Long deviceId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /**
     * 查询时间段内全部设备的数据（用于按天聚合生成功率曲线）
     */
    @Select("SELECT * FROM device_data WHERE ts >= #{start} AND ts < #{end} ORDER BY ts ASC")
    List<DeviceData> selectByTimeRange(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * 统计时间段内功率之和（用于电量积分，功率为 kW，乘时间间隔得 kWh）
     */
    @Select("SELECT COALESCE(SUM(power), 0) FROM device_data WHERE device_id = #{deviceId} AND ts BETWEEN #{start} AND #{end}")
    Double sumPower(@Param("deviceId") Long deviceId,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    /**
     * 统计时间段内正向功率之和（放电/购电/发电）
     */
    @Select("SELECT COALESCE(SUM(power), 0) FROM device_data WHERE device_id = #{deviceId} AND power > 0 AND ts BETWEEN #{start} AND #{end}")
    Double sumPositivePower(@Param("deviceId") Long deviceId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /**
     * 统计时间段内负向功率绝对值之和（充电/上网）
     */
    @Select("SELECT COALESCE(SUM(ABS(power)), 0) FROM device_data WHERE device_id = #{deviceId} AND power < 0 AND ts BETWEEN #{start} AND #{end}")
    Double sumNegativePower(@Param("deviceId") Long deviceId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /**
     * 计算时间段内的平均采样间隔（秒）。
     * 历史数据为 1 分钟/条，实时数据为 simulate-interval-ms（默认 3 秒/条），
     * 按实际间隔折算电量才能得到正确的 kWh，避免两种粒度混算导致数量级错误。
     */
    @Select("SELECT MIN(ts) AS minTs, MAX(ts) AS maxTs, COUNT(*) AS cnt FROM device_data WHERE device_id = #{deviceId} AND ts BETWEEN #{start} AND #{end}")
    DeviceDataStat sampleStat(@Param("deviceId") Long deviceId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);
}
