package com.example.backend.controller;

import com.example.backend.entity.Information;
import com.example.backend.entity.UserProcess;
import com.example.backend.service.InfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/info")
public class InfoConttroller {

    @Autowired
    private InfoService infoService;

    //查询所有信息
    @PostMapping("/selectAllInfo")
    public ResponseEntity<?> selectAllInfo(@RequestBody Map<String,String> request){
        List<Information> information = infoService.selectAllInfo(request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", information);
        return ResponseEntity.ok(response);
    }

    //按需求(服务器IP,组件名称，风险等级，发生时间)查询信息
    @PostMapping("/selectInfo")
    public ResponseEntity<?> select(@RequestBody Map<String,String> request){
        List<Information> information = infoService.selectInfo(request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", information);
        return ResponseEntity.ok(response);
    }

    //存储用户处理记录
    @PostMapping("/insertProcess")
    public ResponseEntity<?> insertProcess(@RequestBody Map<String,String> request){
        String res= infoService.insertProcess(request);
        Map<String, Object> response = new HashMap<>();
        if (res != null && res.contains("成功")){
            response.put("code", 200);
            response.put("msg", res);
            return ResponseEntity.ok(response);
        }else {
            response.put("code", 400);
            response.put("msg", res);
            return ResponseEntity.badRequest().body(response);
        }
    }


    //查寻所有处理记录
    @PostMapping("/selectAllProcess")
    public ResponseEntity<?> selectAllProcess(@RequestBody Map<String,String> request){
        List<UserProcess> information = infoService.selectAllProcess(request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", information);
        return ResponseEntity.ok(response);
    }

    //按需求查询记录
    @PostMapping("/selectProcess")
    public ResponseEntity<?> selectProcess(@RequestBody Map<String,String> request){
        List<UserProcess> information = infoService.selectProcess(request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", information);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deleteAllInfo")
    public ResponseEntity<?> deleteAllInfo(@RequestBody Map<String, String> request) {
        int deleted = infoService.deleteAllInfo(request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "删除成功");
        response.put("data", deleted);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deleteInfo")
    public ResponseEntity<?> deleteInfo(@RequestBody Map<String, String> request) {
        int deleted = infoService.deleteInfo(request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", deleted > 0 ? "删除成功" : "未找到可删除的告警");
        response.put("data", deleted);
        return ResponseEntity.ok(response);
    }

}
