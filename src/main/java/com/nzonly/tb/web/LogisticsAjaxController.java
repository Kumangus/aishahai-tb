package com.nzonly.tb.web;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.servlet.http.HttpSession;

import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.nzonly.tb.entity.Logistics;
import com.nzonly.tb.security.ShiroDbRealm.ShiroUser;
import com.nzonly.tb.service.AuthInfoService;
import com.nzonly.tb.taobao.TaobaoLogisticsService;

/**
 * @author yinheli <yinheli@gmail.com>
 * @date 2012-8-1 下午11:50:13
 * @version V1.0
 */
@Controller
@RequestMapping("/ajax/logistics")
public class LogisticsAjaxController extends BaseController {
	
	/**
	 * 淘宝物流 api service
	 */
	@Autowired
	private TaobaoLogisticsService taobaoLogisticsService;
	
	@Autowired
	private AuthInfoService authInfoService;
	
	private static final String _LOGISTICS_PROCESS_FLAG = "_LOGISTICS_PROCESS_FLAG";
	
	@RequestMapping(value = "/sync", method = RequestMethod.POST)
	@ResponseBody
	public String sync(final HttpSession httpSession, @RequestParam final String start, final @RequestParam String end) {
		final ShiroUser user = (ShiroUser) SecurityUtils.getSubject().getPrincipal();
		httpSession.setAttribute(_LOGISTICS_PROCESS_FLAG, 10);
		// 任务并发提交过多会抛出异常
		executorService.execute(new Runnable() {
			
			@Override
			public void run() {
				try {
					httpSession.setAttribute(_LOGISTICS_PROCESS_FLAG, 20);
					String session = authInfoService.getById(user.authId).getAccessToken();
					if (log.isDebugEnabled()) {
						log.debug("start:{}, end:{}", start, end);
						log.debug("session: {}", session);
					}
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
					Date endDate = sdf.parse(end);
					Calendar cal = Calendar.getInstance();
					cal.setTime(endDate);
					cal.set(Calendar.HOUR_OF_DAY, 23);
					cal.set(Calendar.MINUTE, 59);
					cal.set(Calendar.SECOND, 59);
					
					logisticsOrdersGet(null, sdf.parse(start), cal.getTime(), new PageRequest(0, 50), session);
				} catch (ParseException e) {
					throw new RuntimeException(e);
				} finally {
					httpSession.setAttribute(_LOGISTICS_PROCESS_FLAG, 100);
				}
			}
			
			private void logisticsOrdersGet(Long tid, Date start, Date end, PageRequest pageRequest, String session) {
				Page<Logistics> page;
				try {
					page = taobaoLogisticsService.logisticsOrdersGet(tid, start, end, pageRequest, session);
					setProcess(page);
					if (page != null && !page.getContent().isEmpty()) {
						while (page.hasNextPage()) {
							setProcess(page);
							page = taobaoLogisticsService.logisticsOrdersGet(tid, start, end,
									new PageRequest(page.getNumber() + 1, pageRequest.getPageSize()), session);
						}
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			
			private void setProcess(Page<?> page) {
				log.info("Page:{}, total:{}", page.getNumber(), page.getTotalPages());
				httpSession.setAttribute(_LOGISTICS_PROCESS_FLAG, (page.getNumber() + 1) / page.getTotalPages() * 80 + 20);
			}
		});
		return "SUCCESS";
	}
	
	@RequestMapping(value = "/process")
	@ResponseBody
	public String process(HttpSession httpSession) {
		Object flag = httpSession.getAttribute(_LOGISTICS_PROCESS_FLAG);
		log.debug("process check:{}", flag);
		if (flag == null) {
			return "10";
		}
		if ((Integer)flag == 100) {
			httpSession.removeAttribute(_LOGISTICS_PROCESS_FLAG);
		}
		return  Integer.toString((Integer)flag);
	}

}
