package com.platform.api;

import com.alibaba.fastjson.JSONObject;
import com.platform.annotation.IgnoreAuth;
import com.platform.annotation.LoginUser;
import com.platform.entity.OrderGoodsVo;
import com.platform.entity.OrderVo;
import com.platform.entity.UserCouponVo;
import com.platform.entity.UserVo;
import com.platform.service.*;
import com.platform.util.ApiBaseAction;
import com.platform.util.ApiPageUtils;
import com.platform.util.CrawlPhoneexistornot;
import com.platform.util.wechat.WechatRefundApiResult;
import com.platform.util.wechat.WechatUtil;
import com.platform.utils.Query;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 作者: @author Harmon <br>
 * 时间: 2017-08-11 08:32<br>
 * 描述: ApiIndexController <br>
 */
@Api(tags = "订单相关")
@RestController
@RequestMapping("/api/order")
public class ApiOrderController extends ApiBaseAction {
    @Autowired
    private ApiOrderService orderService;
    @Autowired
    private ApiOrderGoodsService orderGoodsService;
    @Autowired
    private UserRecordSer userRecordSer;
    @Autowired
    private MlsUserSer mlsUserSer;
    @Autowired
    private ApiUserCouponService userCouponService;

    /**
     */
    @ApiOperation(value = "订单首页")
    @IgnoreAuth
    @GetMapping("index")
    public Object index() {
        //
        return toResponsSuccess("");
    }

    /**
     * 获取订单列表
     */
    @ApiOperation(value = "获取订单列表")
    @RequestMapping("list")
    public Object list(@LoginUser UserVo loginUser, Integer order_status,
                       @RequestParam(value = "page", defaultValue = "1") Integer page,
                       @RequestParam(value = "size", defaultValue = "10") Integer size) {
        //
        Map params = new HashMap();
        params.put("user_id", loginUser.getUserId());
        params.put("page", page);
        params.put("limit", size);
        params.put("sidx", "id");
        params.put("order", "desc");
        params.put("order_status", order_status);
        //查询列表数据
        Query query = new Query(params);
        List<OrderVo> orderEntityList = orderService.queryList(query);
        int total = orderService.queryTotal(query);
        ApiPageUtils pageUtil = new ApiPageUtils(orderEntityList, total, query.getLimit(), query.getPage());
        //
        for (OrderVo item : orderEntityList) {
            Map orderGoodsParam = new HashMap();
            orderGoodsParam.put("order_id", item.getId());
            //订单的商品
            List<OrderGoodsVo> goodsList = orderGoodsService.queryList(orderGoodsParam);
            Integer goodsCount = 0;
            for (OrderGoodsVo orderGoodsEntity : goodsList) {
                goodsCount += orderGoodsEntity.getNumber();
                item.setGoodsCount(goodsCount);
            }
        }
        return toResponsSuccess(pageUtil);
    }

    /**
     * 获取订单详情
     */
    @ApiOperation(value = "获取订单详情")
    @GetMapping("detail")
    public Object detail(Integer orderId) {
        Map resultObj = new HashMap();
        //
        OrderVo orderInfo = orderService.queryObject(orderId);
        if (null == orderInfo) {
            return toResponsObject(400, "订单不存在", "");
        }
        Map orderGoodsParam = new HashMap();
        orderGoodsParam.put("order_id", orderId);
        //订单的商品
        List<OrderGoodsVo> orderGoods = orderGoodsService.queryList(orderGoodsParam);
        //订单最后支付时间
        if (orderInfo.getOrder_status() == 0) {
            // if (moment().subtract(60, 'minutes') < moment(orderInfo.add_time)) {
//            orderInfo.final_pay_time = moment("001234", "Hmmss").format("mm:ss")
            // } else {
            //     //超过时间不支付，更新订单状态为取消
            // }
        }

        //订单可操作的选择,删除，支付，收货，评论，退换货
        Map handleOption = orderInfo.getHandleOption();
        //
        resultObj.put("orderInfo", orderInfo);
        resultObj.put("orderGoods", orderGoods);
        resultObj.put("handleOption", handleOption);
        if (!StringUtils.isEmpty(orderInfo.getShipping_code()) && !StringUtils.isEmpty(orderInfo.getShipping_no())) {
            resultObj.put("shippingList", null);
        }
        return toResponsSuccess(resultObj);
    }

    @ApiOperation(value = "修改订单")
    @PostMapping("updateSuccess")
    public Object updateSuccess(Integer orderId) {
        OrderVo orderInfo = orderService.queryObject(orderId);
        if (orderInfo == null) {
            return toResponsFail("订单不存在");
        } else if (orderInfo.getOrder_status() != 0) {
            return toResponsFail("订单状态不正确orderStatus" + orderInfo.getOrder_status() + "payStatus" + orderInfo.getPay_status());
        }

        orderInfo.setId(orderId);
        orderInfo.setPay_status(2);
        orderInfo.setOrder_status(201);
        orderInfo.setShipping_status(0);
        orderInfo.setPay_time(new Date());
        int num = orderService.update(orderInfo);
        if (num > 0) {
            return toResponsMsgSuccess("修改订单成功");
        } else {
            return toResponsFail("修改订单失败");
        }
    }
    
    /**
	 * 上传身份证
	 * @param img
	 * @return
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	@ApiOperation(value = "上传身份证")
	@RequestMapping("uploadCard")
    public Object uploadCard(@LoginUser UserVo loginUser,HttpServletRequest request, HttpServletResponse response) throws IllegalStateException, IOException  {
        MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
        List<MultipartFile> fileList = multiRequest.getFiles("file");
        String sb = "";
        for (MultipartFile mf : fileList) {
            if (!mf.isEmpty()) {
                // 取得当前上传文件的名称
                String myFileName = mf.getOriginalFilename();
                // 如果名称不为""，说明该文件存在，否则说明文件不存在。
                if (myFileName.trim() != "") {
                    System.out.println(myFileName);
                    // 重命名上传后的文件
                    String filename = mf.getOriginalFilename();
                    // 定义上传路劲
                    String path = "/mnt/tomcat/webapps/picture/" + filename;
                    File localFile = new File(path);
                    mf.transferTo(localFile);
                    sb = sb + path + ",";
                }
            }
        }
        
        String result = "success";
        return toResponsSuccess(result);
    }

    /**
     * 获取订单列表
     */
    @ApiOperation(value = "订单提交")
    @PostMapping("submit")
    public Object submit(@LoginUser UserVo loginUser) {
        Map resultObj = null;
        try {
            resultObj = orderService.submit(getJsonRequest(), loginUser);
            if (null != resultObj) {
                return toResponsObject(MapUtils.getInteger(resultObj, "errno"), MapUtils.getString(resultObj, "errmsg"), resultObj.get("data"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toResponsFail("提交失败");
    }
    
    @ApiOperation(value = "商品检查")
    @PostMapping("check")
    public Object check() {
        JSONObject jsonParam = getJsonRequest();
        String name = jsonParam.getString("name");
      //去首页爬取查看该号码是否还存在
        CrawlPhoneexistornot crawlPhone = new CrawlPhoneexistornot();
        try {
			if (!crawlPhone.isExist(name)) {
				return this.toResponsObject(400, "库存不足", "");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        return toResponsMsgSuccess("上传成功");
    }

    /**
     * 获取订单列表
     */
    @ApiOperation(value = "取消订单")
    @RequestMapping("cancelOrder")
    public Object cancelOrder(Integer orderId) {
        try {
            OrderVo orderVo = orderService.queryObject(orderId);
            
            List<OrderVo> orders = orderService.queryByAllOrderId(orderVo.getAll_order_id());
            
            BigDecimal allPrice = BigDecimal.ZERO;
            for(OrderVo o : orders) {
            	allPrice = allPrice.add(o.getAll_price());
            }
            
            if (orderVo.getOrder_status() == 300) {
                return toResponsFail("已发货，不能取消");
            } else if (orderVo.getOrder_status() == 301) {
                return toResponsFail("已收货，不能取消");
            }
            // 需要退款
            if (orderVo.getPay_status() == 2) {
            	
                WechatRefundApiResult result = WechatUtil.wxRefund(orderVo.getAll_order_id().toString(),
                		allPrice.doubleValue(), orderVo.getAll_price().doubleValue());
                //测试修改金额
//                WechatRefundApiResult result = WechatUtil.wxRefund(orderVo.getId().toString(), 0.01d, 0.01d);
                if (result.getResult_code().equals("SUCCESS")) {
                    if (orderVo.getOrder_status() == 201) {
                        orderVo.setOrder_status(401);
                    } else if (orderVo.getOrder_status() == 300) {
                        orderVo.setOrder_status(402);
                    }
                    
                    orderVo.setPay_status(4);
                    orderService.update(orderVo);
                    
                    //更新优惠券状态和实际
                    UserCouponVo uc = new UserCouponVo();
        			uc.setId(orderVo.getCoupon_id());
        			uc.setCoupon_status(1);
        			uc.setUsed_time(null);
        			userCouponService.updateCouponStatus(uc);
                    
                    //去掉订单成功成立分润退还
                    try {
                    	orderService.cancelFx(orderVo.getId(), orderVo.getPay_time(), orderVo.getAll_price().multiply(new BigDecimal("100")).intValue());
                    }catch(Exception e) {
                    	System.out.println("================取消订单返还分润开始================");
                    	e.printStackTrace();
                    	System.out.println("================取消订单返还分润开始================");
                    }
                    return toResponsSuccess("取消成功");
                } else {
                    return toResponsObject(400, "取消成失败", "取消成失败");
                }
            } else {
                orderVo.setOrder_status(101);
                orderService.update(orderVo);
                return toResponsSuccess("取消成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toResponsSuccess("提交失败");
    }
    


    /**
     * 确认收货
     */
    @ApiOperation(value = "确认收货")
    @RequestMapping("confirmOrder")
    public Object confirmOrder(Integer orderId) {
        try {
            OrderVo orderVo = orderService.queryObject(orderId);
            orderVo.setOrder_status(301);
            orderVo.setShipping_status(2);
            orderVo.setConfirm_time(new Date());
            orderService.update(orderVo);
            return toResponsSuccess("确认收货成功");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toResponsFail("提交失败");
    }
}