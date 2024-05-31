package com.nanshuo.BI.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nanshuo.BI.annotation.Check;
import com.nanshuo.BI.common.ApiResponse;
import com.nanshuo.BI.common.ApiResult;
import com.nanshuo.BI.common.ErrorCode;
import com.nanshuo.BI.constant.PageConstant;
import com.nanshuo.BI.constant.UserConstant;
import com.nanshuo.BI.exception.BusinessException;
import com.nanshuo.BI.model.domain.Chart;
import com.nanshuo.BI.model.domain.User;
import com.nanshuo.BI.model.dto.IdRequest;
import com.nanshuo.BI.model.dto.chart.ChartAddRequest;
import com.nanshuo.BI.model.dto.chart.ChartEditRequest;
import com.nanshuo.BI.model.dto.chart.ChartQueryRequest;
import com.nanshuo.BI.model.dto.chart.ChartUpdateRequest;
import com.nanshuo.BI.service.ChartService;
import com.nanshuo.BI.service.UserService;
import com.nanshuo.BI.utils.SqlUtils;
import com.nanshuo.BI.utils.ThrowUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

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
    private ChartService ChartService;
    @Resource
    private UserService userService;

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
        boolean result = ChartService.save(Chart);
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
        return ApiResult.success(ChartService.removeById(id));
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
        Chart oldChart = ChartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        return ApiResult.success(ChartService.updateById(Chart));
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
        boolean result = ChartService.updateById(Chart);
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
        Chart chart = ChartService.getById(id);
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
        Page<Chart> chartPage = ChartService.page(new Page<>(current, size), getQueryWrapper(ChartQueryRequest));
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
        Chart oldChart = ChartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可操作（这里假设"编辑"和"删除"操作的权限是一样的）
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    // endregion
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
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(PageConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}
