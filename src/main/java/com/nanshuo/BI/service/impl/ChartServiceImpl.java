package com.nanshuo.BI.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nanshuo.BI.model.domain.Chart;
import com.nanshuo.BI.service.ChartService;
import com.nanshuo.BI.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author dell
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2024-05-31 20:46:55
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




