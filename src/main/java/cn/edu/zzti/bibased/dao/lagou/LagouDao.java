package cn.edu.zzti.bibased.dao.lagou;

import cn.edu.zzti.bibased.constant.WebsiteEnum;
import cn.edu.zzti.bibased.dao.mapper.CityMapper;
import cn.edu.zzti.bibased.dao.mapper.CompanyMapper;
import cn.edu.zzti.bibased.dao.mapper.PositionDetailMapper;
import cn.edu.zzti.bibased.dao.mapper.PositionsMapper;
import cn.edu.zzti.bibased.dto.City;
import cn.edu.zzti.bibased.dto.Company;
import cn.edu.zzti.bibased.dto.PositionDetail;
import cn.edu.zzti.bibased.dto.Positions;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用于数据的写入
 */
@Repository
public class LagouDao {
    @Resource
    private PositionsMapper positionsMapper;

    @Resource
    private CityMapper cityMapper;

    @Resource
    private CompanyMapper companyMapper;

    @Resource
    private PositionDetailMapper positionDetailMapper;

    public void insertJob(Positions position){
        positionsMapper.insert(position);
    }
    public void batchInsertJobs(List<Positions> positionList){
        positionsMapper.batchInsert(positionList);
    }

    public void batchInsertCitys(List<City> cityList){
        cityMapper.batchInsert(cityList);
    }

    public void batchInsertCompanys(List<Company> companies){
        companyMapper.batchInsert(companies);
    }

    public List<Positions> queryLeafPositions(String include){
        return positionsMapper.queryLeafPositions(include);
    }

    public List<City> queryCitys(String include){
        return cityMapper.queryCity(include);
    }

    public void batchInsertPositionDetails(List<PositionDetail> positionDetails){
        positionDetailMapper.batchInsert(positionDetails);
    }

    public Long queryLastPositionCreateTime(){
        PositionDetail positionDetail = positionDetailMapper.selectLastPostionCreateTime(WebsiteEnum.LAGOU.getWebCode());
        return positionDetail !=null?positionDetail.getPositionCreateTime():0L;
    }


}
