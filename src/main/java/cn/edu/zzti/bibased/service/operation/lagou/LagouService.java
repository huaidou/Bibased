package cn.edu.zzti.bibased.service.operation.lagou;

import cn.edu.zzti.bibased.constant.HttpHeaderConstant;
import cn.edu.zzti.bibased.constant.HttpType;
import cn.edu.zzti.bibased.constant.WebsiteEnum;
import cn.edu.zzti.bibased.dto.City;
import cn.edu.zzti.bibased.dto.Company;
import cn.edu.zzti.bibased.dto.PositionDetail;
import cn.edu.zzti.bibased.dto.Positions;
import cn.edu.zzti.bibased.dto.lagou.*;
import cn.edu.zzti.bibased.execute.BaseExecuter;
import cn.edu.zzti.bibased.execute.CompanyExecute;
import cn.edu.zzti.bibased.execute.PositionDetailExecute;
import cn.edu.zzti.bibased.service.handler.LagouHandler;
import cn.edu.zzti.bibased.service.http.HttpClientService;
import cn.edu.zzti.bibased.thread.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;

@Service
public class LagouService {
    private static final Logger logger = LoggerFactory.getLogger(LagouService.class);
    @Resource
    private ThreadPoolTaskExecutor lagouPool;
    /**
     * 注入无阻塞的
     */
    @Resource
    private CompletionService completionService;
    @Resource
    private LagouOperationService lagouOperationService;
    @Resource
    private HttpClientService httpClientService;

    public String startGetDate(String apiUrl, Map<String,Object> param,String httpType) throws Exception{
        LaGouTask laGouTask = new LaGouTask(apiUrl,param, httpType);
        for (int i = 0; i < 5; i++) {
            completionService.submit(laGouTask);
        }
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 5; i++) {
            Future<String> take = completionService.take();
            if (take !=null) {
                buffer.append("第几次：" + i + "\n" + take.get()+"\n\n\n\n      \n");
            }
        }
        return buffer.toString();
    }

    /**
     * 多线程执行数据
     */
    public void initLagouInfo(){
        lagouPool.execute(()-> {
                collectionJobInformation();
        });
        lagouPool.execute(()->{
            collectionCityInformation();
        });

    }
    /**
     * 采集拉勾网的职位分类信息
     *
     */
    private void collectionJobInformation(){
        String url = "https://www.lagou.com";
        String html = httpClientService.doGet(url, null, HttpHeaderConstant.lagouGetHeader);
        List<Positions> jobs = LagouHandler.getJobs(html);
        lagouOperationService.batchAddJob(jobs);
    }
    /**
     * 采集拉勾网的城市信息
     *
     */
    private void collectionCityInformation(){
        String url = "https://www.lagou.com/zhaopin/Java/?labelWords=label";
        String html = httpClientService.doGet(url, null, HttpHeaderConstant.lagouGetHeader);
        List<City> jobs = LagouHandler.getCitys(html);
        lagouOperationService.batchAddCity(jobs);
    }
    /**
     * 采集拉勾网的公司信息
     */
    public void collectionCompanyInfomation(){
        String apiUrl = "https://www.lagou.com/gongsi/";
        String html = httpClientService.doGet(apiUrl, null, HttpHeaderConstant.lagouGetHeader);
        List<City> cityByCompany = LagouHandler.getCityByCompany(html);
        //去掉第一个和最后一个
        for (int i = 1; i < cityByCompany.size()-1; i++) {
            Gson gson = new Gson();
            String url = apiUrl+cityByCompany.get(i).getLinkId()+"-0-0.json";
            logger.info(url);
            Map<String,Object> param = new LinkedHashMap<>();
            param.put("first",false);
            param.put("pn",1);
            param.put("sortField",0);
            param.put("havemark",0);
            String data = httpClientService.doPost(url, param, HttpHeaderConstant.lagouAjaxHeader);
            CompanyResultJsonVO companyResultJsonVO = gson.fromJson(data, CompanyResultJsonVO.class);
            int pageNo = companyResultJsonVO.getTotalCount()/companyResultJsonVO.getPageSize();
            logger.info("-----------page:"+pageNo+"\n");
            LaGouTask laGouTask = new LaGouTask(url,param, HttpType.POST);
            List<CompanyVO> resultVOS = new LinkedList<>();
            resultVOS.addAll(companyResultJsonVO.getResult());
            for (int j = 2; j <= pageNo; j++) {
                for (int k = 0; k < 10; k++) {
                    param.put("first",false);
                    param.put("pn",j);
                    completionService.submit(laGouTask);
                    j++;
                }
                logger.info("-------------------------->"+j+"\n");
                for (int k = 0; k < 10; k++) {
                    try{
                        Future<String> take = completionService.take();
                        if(take.get()!=null){
                            CompanyResultJsonVO companyResultJsonVO1 = gson.fromJson(take.get(), CompanyResultJsonVO.class);
                            List<CompanyVO> result = companyResultJsonVO1.getResult();
                            resultVOS.addAll(result);
                        }
                    }catch (Exception e){
                        logger.error(e.getMessage(),e);
                    }
                }

                if (resultVOS.size() > 0) {
                    handleCompany(resultVOS);
                }else{
                    break;
                }
            }
            try {
                Thread.sleep(3000);
            }catch (Exception e){}
        }
    }
    public void collectionCompanyInfomationV2(){
        String apiUrl = "https://www.lagou.com/gongsi/";
        String html = httpClientService.doGet(apiUrl, null, HttpHeaderConstant.lagouGetHeader);
        List<City> cityByCompany = LagouHandler.getCityByCompany(html);
        //去掉第一个和最后一个
        for (int i = 1; i < cityByCompany.size()-1; i++) {
            Gson gson = new Gson();
            String url = apiUrl+cityByCompany.get(i).getLinkId()+"-0-0.json";
            logger.info(url);
            Map<String, Object> lagouAjaxHeader = HttpHeaderConstant.lagouAjaxHeader;
            lagouAjaxHeader.put("Referer",url.replace(".json",""));
            String data = httpClientService.doPost(url, HttpHeaderConstant.compaanyParam, lagouAjaxHeader);
            CompanyResultJsonVO companyResultJsonVO = gson.fromJson(data, CompanyResultJsonVO.class);
            int pageNo = companyResultJsonVO.getTotalCount()/companyResultJsonVO.getPageSize();
            logger.info("-----------page:"+pageNo+"\n");
            BaseExecuter companyTask = new CompanyExecute();
            Map<String, Object> companyParam = HttpHeaderConstant.compaanyParam;
            companyTask.setApiUrl(url);
            companyTask.setHeaders(lagouAjaxHeader);
            companyTask.setParams(companyParam);
            List<CompanyVO> resultVOS = new LinkedList<>();
            resultVOS.addAll(companyResultJsonVO.getResult());
            for (int j = 2; j <= pageNo; j++) {
                for (int k = 0; k < 10; k++) {
                    companyParam.put("first",false);
                    companyParam.put("pn",j);
                    lagouAjaxHeader.put("Referer",url.replace(".json",""));
                    String cookie = lagouAjaxHeader.get("Cookie").toString()+ UUID.randomUUID().toString().replace("-","").toString()+";";
                    lagouAjaxHeader.put("Cookie",cookie);
                    completionService.submit(AnsyTask.newTask().registExecuter(companyTask));
                    j++;
                }
                logger.info("-------------------------->"+j+"\n");
                for (int k = 0; k < 10; k++) {
                    try{
                        Future<CompanyResultJsonVO> take = completionService.take();
                        if(take.get()!=null){
                            CompanyResultJsonVO companyResultJsonVO1 = take.get();
                            List<CompanyVO> result = companyResultJsonVO1.getResult();
                            resultVOS.addAll(result);
                        }
                    }catch (Exception e){
                        logger.error(e.getMessage(),e);
                    }
                }

                if (resultVOS.size() > 0) {
                    handleCompany(resultVOS);
                    return;
                }else{
                    break;
                }
            }
            try {
                Thread.sleep(3000);
            }catch (Exception e){}
        }
    }
    void handleCompany(List<CompanyVO> companyVOS){
        if(!CollectionUtils.isEmpty(companyVOS)){
            List<Company> targetCompany = new LinkedList<>();
            for (CompanyVO vo:companyVOS) {
                Company company = new Company();
                targetCompany.add(company);
                BeanUtils.copyProperties(vo,company);
                company.setCompanyName(vo.getCompanyShortName());
                company.setCompanyUrl("https://www.lagou.com/gongsi/"+vo.getCompanyId());
                company.setResumeRate(vo.getProcessRate());
                company.setInclude(WebsiteEnum.LAGOU.getWebCode());
                company.setPositionSlogan(vo.getCompanyFeatures());
            }
            companyVOS.clear();
            lagouOperationService.batchAddCompany(targetCompany);
        }
    }

    public void connectionPositionDeailsInfomation(){
        Future<List<Positions>> positionListFuter = lagouPool.submit(() -> {
               return lagouOperationService.queryLeftPositions();
        });
        List<Positions> positions = null;
        List<City> citys =lagouOperationService.queryCitys();
        try{
            positions = positionListFuter.get();
        }catch (Exception e){}
        if(!CollectionUtils.isEmpty(positions) && !CollectionUtils.isEmpty(citys)){
            positions.forEach(position -> {
                String positionName = position.getPositionName();
                Map<String,Object> param = new LinkedHashMap<>();
                param.put("first",true);
                param.put("pn",1);
                param.put("kd",positionName);
                Map<String, Object> lagouAjaxHeader = HttpHeaderConstant.lagouAjaxHeader;
                citys.stream().forEach(city -> {
                    String apiUrl = "https://www.lagou.com/jobs/positionAjax.json?px=default&city="+city.getCityName()+"&needAddtionalResult=false&isSchoolJob=0";
                    logger.info("---->  "+apiUrl);
                    lagouAjaxHeader.put("Referer","https://www.lagou.com/jobs/list_"+position.getPositionName()+"?px=default&city="+city.getCityName());
                    setCookie(lagouAjaxHeader);
                    String data = httpClientService.doPost(apiUrl, param, lagouAjaxHeader);
                    int pageSize = this.getPageSize(data);
                    if(pageSize != 0){
                        logger.info("---->执行数据");
                        for(int i=1;i<=pageSize;i++){
                            logger.info("---->  "+i);
                            for (int j=0;j<10;j++){
                                param.put("first",false);
                                param.put("pn",i);
                                setCookie(lagouAjaxHeader);
                                BaseExecuter positonDetailTask = new PositionDetailExecute();
                                positonDetailTask.setParams(param);
                                positonDetailTask.setHeaders(lagouAjaxHeader);
                                positonDetailTask.setApiUrl(apiUrl);
                                completionService.submit(AnsyTask.newTask().registExecuter(positonDetailTask));
                                i++;
                            }
                            boolean isGo = true;
                            List<PositionDetail> positionDetails = new LinkedList<>();
                            for (int k = 0; k <10; k++) {
                                try {
                                    Future<PositionDetailResultJsonVo> take = completionService.take();
                                    if(take.get() != null){
                                        PositionDetailResultJsonVo positionDetailResultJsonVo = take.get();
                                        List<PositionDetailVo> result = positionDetailResultJsonVo.getResult();
                                        positionDetails.addAll(handlePositionDetails(result));
                                        if(result !=null || result.size() == 0) {
                                            isGo =false;
                                        }
                                    }
                                }catch (Exception e){
                                    isGo = false;
                                    logger.error("获取数据失败！",e);
                                }
                            }

                            if(positionDetails.size()>0 ){
                                lagouOperationService.batchAddPositionDetails(positionDetails);
                                logger.info("----写入数据>  ");
                            }else if(!isGo || positionDetails.size() == 0){
                                break;
                            }

                          }

                    }

                });

            });

        }
    }

    private int getPageSize(String sourceJson){
        String targetJson = null;
        try {
            JsonElement jsonElement = new JsonParser().parse(sourceJson);
            targetJson =  jsonElement.getAsJsonObject().get("content").getAsJsonObject().get("positionResult").toString();
        }catch (Exception e){
            logger.error("职位json获取值失败",e);
        }
        Gson gson = new Gson();
        PositionDetailResultJsonVo positionDetailResultJsonVo = gson.fromJson(targetJson == null ? "{}" : targetJson, PositionDetailResultJsonVo.class);
        if(positionDetailResultJsonVo !=null){
            return positionDetailResultJsonVo.getTotalCount()/positionDetailResultJsonVo.getResultSize();
        }
        return 0;

    }

    private List<PositionDetail> handlePositionDetails(List<PositionDetailVo> positionDetailVos){
        List<PositionDetail>   positionDetails = new LinkedList<>();
        if(!CollectionUtils.isEmpty(positionDetailVos)){
            positionDetailVos.forEach(positionDetailVo -> {
                PositionDetail positionDetail = new PositionDetail();
                positionDetails.add(positionDetail);
                BeanUtils.copyProperties(positionDetailVo,positionDetail);
                String gps = new Gson().toJson(new GPS(positionDetailVo.getLongitude(), positionDetailVo.getLatitude()));
                positionDetail.setGps(gps);
                String[] workYears = positionDetailVo.getWorkYear().replace("年", "").replace("不限","").split("-");
                if(workYears==null || workYears.length==0 || workYears.length ==1){
                    positionDetail.setWorkMaxYear(0);
                    positionDetail.setWorkMinYear(0);
                }else if(workYears.length ==1){
                    positionDetail.setWorkMaxYear(Integer.valueOf(workYears[0]));
                    positionDetail.setWorkMinYear(Integer.valueOf(workYears[0]));

                }else{
                    positionDetail.setWorkMaxYear(Integer.valueOf(workYears[1]));
                    positionDetail.setWorkMinYear(Integer.valueOf(workYears[0]));
                }
                String []salary = positionDetailVo.getSalary() != null? positionDetail.getSalary().toLowerCase().replace("k","").split("-"): null;
                if(salary ==null || salary.length ==0){
                    positionDetail.setMinSalary(new BigDecimal(Double.valueOf(0)));
                    positionDetail.setMaxSalary(new BigDecimal(Double.valueOf(0)));
                }else if(salary.length ==1){
                    positionDetail.setMinSalary(new BigDecimal(Double.valueOf(salary[0])));
                    positionDetail.setMaxSalary(new BigDecimal(Double.valueOf(salary[0])));
                }else{
                    positionDetail.setMinSalary(new BigDecimal(Double.valueOf(salary[0])));
                    positionDetail.setMaxSalary(new BigDecimal(Double.valueOf(salary[1])));
                }
                String []companySize =   positionDetailVo.getCompanySize() !=null ? positionDetailVo.getCompanySize().replace("少于","").replace("人以上","").replace("人","").split("-"):null;

                if(companySize ==null || companySize.length==0){
                    positionDetail.setCompanyMaxSize(0);
                    positionDetail.setCompanyMinSize(0);
                }else if(companySize.length ==1){
                    positionDetail.setCompanyMaxSize(Integer.valueOf(companySize[0]));
                    positionDetail.setCompanyMinSize(Integer.valueOf(companySize[0]));
                }else{
                    positionDetail.setCompanyMaxSize(Integer.valueOf(companySize[1]));
                    positionDetail.setCompanyMinSize(Integer.valueOf(companySize[0]));
                }
                positionDetail.setCompanyLabelList(positionDetailVo.getCompanyLabelList() != null ?positionDetailVo.getCompanyLabelList().toString():null);
                positionDetail.setBusinessZones(positionDetailVo.getBusinessZones() !=null ? positionDetailVo.getBusinessZones().toString() : null);
                positionDetail.setPositionLables(positionDetailVo.getPositionLables() !=null ? positionDetailVo.getPositionLables().toString() : null);
                positionDetail.setIndustryField(positionDetailVo.getIndustryField() !=null ? positionDetailVo.getIndustryField().toString() : null);
            });
        }
        return positionDetails;
    }

    private void setCookie(Map<String,Object> header){
        String cookie = header.get("Cookie").toString()+ UUID.randomUUID().toString().replace("-","").toString()+";";
        header.put("Cookie",cookie);
    }
}
