package com.free.route.service.impl;

import com.alibaba.fastjson.JSON;
import com.free.common.utils.TokenUtil;
import com.free.common.constant.StatusEnum;
import com.free.route.service.AccountService;
import com.free.route.vo.LoginReqVO;
import com.free.route.vo.ReqisterVo;
import com.free.route.vo.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.free.common.constant.RedisKeyConstant.*;


@Service
public class AccountServiceImpl implements AccountService {

    private Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Autowired
    private RedisTemplate<String,String> redisTemplate ;

    @Override
    public UserVo reqister(ReqisterVo reqisterVo) {

        String idKey = redisTemplate.opsForValue().get(reqisterVo.getUserName());
        UserVo newUser = null;
        if(idKey == null){
            Long userId = redisTemplate.opsForValue().increment(USER_ID_CREATE,1);
            idKey = USER_PREF+userId;
            newUser = new UserVo(userId,reqisterVo.getUserName(), TokenUtil.generateToken(userId));
            String userInfo = JSON.toJSONString(newUser);

            redisTemplate.opsForValue().set(idKey,reqisterVo.getUserName());
            redisTemplate.opsForValue().set(reqisterVo.getUserName(),idKey);
            redisTemplate.opsForValue().set(USER_STATUS+userId,userInfo);
        }else {
            long userId = Long.parseLong(idKey.split(":")[1]);
            newUser = new UserVo(userId,reqisterVo.getUserName());
        }

        return newUser;
    }

    @Override
    public StatusEnum login(LoginReqVO loginReqVO){
        StatusEnum  statusEnum = StatusEnum.SUCCESS;
        String key = redisTemplate.opsForValue().get(loginReqVO.getUserName());
        if(!StringUtils.isEmpty(key)){
            long userId = Long.parseLong(key.split(":")[1]);
            String userJson = redisTemplate.opsForValue().get(USER_STATUS+userId);
            UserVo  userVo = JSON.parseObject(userJson,UserVo.class);
            if (!userVo.getToken().equals(loginReqVO.getPassWord())){
                statusEnum =  StatusEnum.PASSWORD_FAILT;
            }
        }else {
            statusEnum =  StatusEnum.ACCOUNT_NOT_MATCH;
        }

        return statusEnum;
    }

    @Override
    public void saveRouteInfo(Long userId, String brokerName) {
        redisTemplate.opsForValue().set(USER_BROKER+userId,brokerName);
    }

    public void offerLine(Long userId){
        redisTemplate.delete(USER_BROKER+userId);
        //redisTemplate.delete(USER_STATUS+userId);
        //redisTemplate.delete(USER_STATUS+userId);
    }

}
