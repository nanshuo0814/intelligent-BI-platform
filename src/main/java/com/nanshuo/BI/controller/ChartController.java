
package com.nanshuo.BI.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nanshuo.BI.annotation.Check;
import com.nanshuo.BI.common.ApiResponse;
import com.nanshuo.BI.common.ApiResult;
import com.nanshuo.BI.common.CommonConstant;
import com.nanshuo.BI.common.ErrorCode;
import com.nanshuo.BI.constant.PageConstant;
import com.nanshuo.BI.constant.UserConstant;
import com.nanshuo.BI.exception.BusinessException;
import com.nanshuo.BI.manager.AiManager;
import com.nanshuo.BI.manager.RedisLimiterManager;
import com.nanshuo.BI.model.domain.Chart;
import com.nanshuo.BI.model.domain.User;
import com.nanshuo.BI.model.dto.IdRequest;
import com.nanshuo.BI.model.dto.chart.*;
import com.nanshuo.BI.model.vo.BiResponse;
import com.nanshuo.BI.service.ChartService;
import com.nanshuo.BI.service.UserService;
import com.nanshuo.BI.utils.ExcelUtils;
import com.nanshuo.BI.utils.SqlUtils;
import com.nanshuo.BI.utils.ThrowUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 图表接口
 *
 * @author nanshuo
 * @date 2024/03/31 11:40:08
 */
@RestController
@RequestMapping("/chart")
@Slf4j
@Api(tags = "图表模块")
public class ChartController {

    @Resource
    private UserService userService;
    @Resource
    private RedisLimiterManager redisLimiterManager;
    @Resource
    private ChartService chartService;
    @Resource
    private AiManager aiManager;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;


    // region 增删改查

    /**
     * 添加图表
     *
     * @param chartAddRequest Chart添加请求
     * @param request        请求
     * @return {@code ApiResponse<Long>}
     */
    @PostMapping("/add")
    @Check(checkParam = true, checkAuth = UserConstant.USER_ROLE)
    @ApiOperation(value = "添加图表", notes = "添加图表")
    public ApiResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, Chart);
        User loginUser = userService.getLoginUser(request);
        Chart.setUserId(loginUser.getId());
        boolean result = chartService.save(Chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = Chart.getId();
        return ApiResult.success(newChartId);
    }

    /**
     * 删除图表
     *
     * @param idRequest 删除请求
     * @param request   请求
     * @return {@code ApiResponse<Boolean>}
     */
    @PostMapping("/delete")
    @Check(checkAuth = UserConstant.USER_ROLE)
    @ApiOperation(value = "删除图表", notes = "删除图表")
    public ApiResponse<Boolean> deleteChart(@RequestBody IdRequest idRequest, HttpServletRequest request) {
        if (idRequest == null || idRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = idRequest.getId();
        validateAndCheckAuthForChartOperation(request, id);
        return ApiResult.success(chartService.removeById(id));
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest 更新后请求
     * @return {@code ApiResponse<Boolean>}
     */
    @PostMapping("/update")
    @Check(checkAuth = UserConstant.ADMIN_ROLE)
    @ApiOperation(value = "更新图表（仅管理员）", notes = "更新图表（仅管理员）")
    public ApiResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, Chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        return ApiResult.success(chartService.updateById(Chart));
    }

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest Chart编辑请求
     * @param request         请求
     * @return {@code ApiResponse<Boolean>}
     */
    @PostMapping("/edit")
    @Check(checkAuth = UserConstant.USER_ROLE, checkParam = true)
    @ApiOperation(value = "编辑图表", notes = "编辑图表")
    public ApiResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {

        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, Chart);
        validateAndCheckAuthForChartOperation(request, chartEditRequest.getId());
        boolean result = chartService.updateById(Chart);
        return ApiResult.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param request   请求
     * @param idRequest id请求
     * @return {@code ApiResponse<Chart>}
     */
    @GetMapping("/get")
    @Check(checkParam = true)
    @ApiOperation(value = "根据 id 获取", notes = "根据 id 获取")
    public ApiResponse<Chart> getChartById(IdRequest idRequest, HttpServletRequest request) {
        if (idRequest == null || idRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = idRequest.getId();
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ApiResult.success(chart);
    }

    // endregion

    // region 分页查询

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest Chart查询请求
     * @param request          请求
     * @return {@code ApiResponse<Page<Chart>>}
     */
    @PostMapping("/list/page")
    @ApiOperation(value = "分页获取列表（封装类）", notes = "分页获取列表（封装类）")
    public ApiResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                      HttpServletRequest request) {
        return ApiResult.success(handlePaginationAndValidation(chartQueryRequest, request));
    }

    /**
     * 分页获取当前用户创建的图表列表
     *
     * @param ChartQueryRequest Chart查询请求
     * @param request          请求
     * @return {@code ApiResponse<Page<Chart>>}
     */
    @PostMapping("/my/list/page")
    @ApiOperation(value = "分页获取用户创建的图表", notes = "分页获取用户创建的图表")
    public ApiResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest ChartQueryRequest,
                                                        HttpServletRequest request) {
        if (ChartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ChartQueryRequest.setUserId(loginUser.getId());
        return ApiResult.success(handlePaginationAndValidation(ChartQueryRequest, request));
    }

    /**
     * 处理分页和验证
     *
     * @param ChartQueryRequest Chart查询请求
     * @param request          请求
     * @return {@code Page<Chart>}
     */
    private Page<Chart> handlePaginationAndValidation(ChartQueryRequest ChartQueryRequest, HttpServletRequest request) {
        long current = ChartQueryRequest.getCurrent();
        long size = ChartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage =chartService.page(new Page<>(current, size), getQueryWrapper(ChartQueryRequest));
        return chartPage;
    }

    // endregion

    // region 公用方法

    /**
     * 验证并检查图表操作权限
     *
     * @param request 请求
     * @param id      id
     */
    private void validateAndCheckAuthForChartOperation(HttpServletRequest request, Long id) {
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可操作（这里假设"编辑"和"删除"操作的权限是一样的）
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chart_type", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "user_id", userId);
        queryWrapper.eq("is_delete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(PageConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    // endregion

    /**
     * gen图表 (按ai)
     *
     * @param multipartFile       多部分文件
     * @param genChartByAiRequest 按ai请求生成图表
     * @param request             请求
     * @return {@link ApiResponse }<{@link BiResponse }>
     */
    @PostMapping("/gen")
    @ApiOperation(value = "生成图表", notes = "生成图表")
    public ApiResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = CommonConstant.BI_MODEL_ID;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        String result = aiManager.doChat(biModelId, userInput.toString());
        String[] splits = result.split("【【【【【");
        if (splits.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ApiResult.success(biResponse);
    }

    /**
     * 通过ai异步生成图表
     *
     * @param multipartFile       多部分文件
     * @param genChartByAiRequest 按ai请求生成图表
     * @param request             请求
     * @return {@link ApiResponse }<{@link BiResponse }>
     */
    @PostMapping("/gen/async")
    public ApiResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = 1659171950288818178L;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // todo 建议处理任务队列满了后，抛异常的情况
        CompletableFuture.runAsync(() -> {
            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            if (!b) {
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                return;
            }
            // 调用 AI
            String result = aiManager.doChat(biModelId, userInput.toString());
            String[] splits = result.split("【【【【【");
            if (splits.length < 3) {
                handleChartUpdateError(chart.getId(), "AI 生成错误");
                return;
            }
            String genChart = splits[1].trim();
            String genResult = splits[2].trim();
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            // todo 建议定义状态为枚举值
            updateChartResult.setStatus("succeed");
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }
        }, threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ApiResult.success(biResponse);
    }



    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage("execMessage");
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

}
