package com.example.backend.service;

import com.example.backend.entity.Information;
import com.example.backend.entity.UserProcess;

import java.util.List;
import java.util.Map;

public interface InfoService {

    List<Information> selectAllInfo(Map<String, String> request);

    List<Information> selectInfo(Map<String, String> request);

    String insertProcess(Map<String, String> request);

    List<UserProcess> selectAllProcess(Map<String, String> request);

    List<UserProcess> selectProcess(Map<String, String> request);

    int deleteAllInfo(Map<String, String> request);

    int deleteInfo(Map<String, String> request);

    int deleteInfoByServerIp(Map<String, String> request);
}
