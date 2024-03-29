package com.trust.xfyl.service;

import com.aliyuncs.exceptions.ClientException;
import com.trust.xfyl.dao.TrustFileMapper;
import com.trust.xfyl.dao.TrustRelationFileMapper;
import com.trust.xfyl.entity.ResultVO;
import com.trust.xfyl.entity.TrustFile;
import com.trust.xfyl.entity.TrustRelationFile;
import com.trust.xfyl.util.AliSkinUtils;
import com.trust.xfyl.util.ResultVOUtil;
import com.trust.xfyl.util.SkinDetection;
import com.trust.xfyl.util.StsServiceSample;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static com.trust.xfyl.util.AliSkinUtils.DF;

/**
 * @author LENOVO
 */
@Service
public class FileService {
    private static String uploadImgUrl;
    private final TrustFileMapper trustFileMapper;
    private final TrustRelationFileMapper trustRelationFileMapper;
    String accessKeyId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
    String keySecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");

    public FileService(TrustFileMapper trustFileMapper, TrustRelationFileMapper trustRelationFileMapper) {
        this.trustFileMapper = trustFileMapper;
        this.trustRelationFileMapper = trustRelationFileMapper;
    }
    @Value("${imageurl.uploadImgUrl}")
    public void setUploadImgUrl(String uploadImgUrl) {
        FileService.uploadImgUrl = uploadImgUrl;
    }


    public static boolean processImage(MultipartFile multipartFile) throws IOException {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("temp", null);
            multipartFile.transferTo(tempFile);
            String realPath = tempFile.getAbsolutePath();

            // 在尝试删除文件之前检查文件是否被占用
            if (!isFileInUse(tempFile)) {
                return SkinDetection.skinDetection(realPath);
            } else {
                System.out.println("文件正在被其他进程占用，无法处理。");
                return false;
            }
        } finally {
            if (tempFile != null) {
                Files.delete(tempFile.toPath());
            }
        }
    }

    private static boolean isFileInUse(File file) {
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            randomAccessFile.close();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * @return void
     * @Author djj
     * @Description //TODO
     * @Date 16:00 2024/1/25
     * @Param [returnMap, file]
     **/
    public void upload(Map<String, Object> returnMap, List<MultipartFile> files) throws IOException, NoSuchFieldException, ClientException {
        List<TrustFile> trustFiles = new ArrayList<>();
        // 调用修改后的uploadFiles方法获取文件URL列表
        ResultVO resultVO = StsServiceSample.uploadFiles(files);
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) resultVO.getData();
        for (Map<String, Object> fileMap : dataList) {
            Object fileName = fileMap.get("fileName");
            Object fileUrl = fileMap.get("fileUrl");
            Object originalFilename = fileMap.get("originalFilename");
            TrustFile trustFile = new TrustFile();
            trustFile.setIsDelete(0);
            trustFile.setCreateTime(new Date());
            trustFile.setFileName(fileName.toString());
            trustFile.setFileOriginName(originalFilename.toString());
            trustFile.setFileUrl((String) fileUrl);
            trustFiles.add(trustFile);
        }
        if (dataList.size() > 0) {
            // 进行数据库插入操作
            List<TrustFile> afterInsertFile = addImg(trustFiles);
            System.out.println("插入成功");

            returnMap.put("status", 0);
            returnMap.put("msg", "上传成功");
        } else {
            // 处理上传失败情况
            returnMap.put("status", -1);
            returnMap.put("msg", "上传失败");
            System.out.println("上传失败");
        }
    }

    public ResultVO getWoundInformation(Map<String, Object> returnMap, List<MultipartFile> files) throws Exception {
        ResultVO resultVO = StsServiceSample.uploadFiles(files);
        /* ResultVO resultVO = StsServiceSample.downFiles(files);*/
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) resultVO.getData();
        if (dataList.isEmpty()) {
            return ResultVOUtil.error(-1, "文件上传失败");
        }

        List<TrustFile> trustFiles = dataList.stream().map(customClass -> {
            TrustFile trustFile = new TrustFile();
            trustFile.setIsDelete(0);
            trustFile.setCreateTime(new Date());
            trustFile.setFileName((String) customClass.get("fileName"));
            trustFile.setFileOriginName((String) customClass.get("originalFilename"));
            trustFile.setFileUrl((String) customClass.get("fileUrl"));
            return trustFile;
        }).collect(Collectors.toList());

        List<TrustFile> afterInsertFile = addImg(trustFiles);

        if (afterInsertFile.isEmpty()) {
            return ResultVOUtil.error(-1, "数据插入异常");
        }

        try {
            List<List<String>> woundInformationLists = new ArrayList<>();
            DF.setTimeZone(new java.util.SimpleTimeZone(0, "GMT"));
            for (TrustFile trustFile : afterInsertFile) {
                String fileUrl = trustFile.getFileUrl();
                JSONObject skinDetectionResult = AliSkinUtils.execute("DetectSkinDisease", accessKeyId, keySecret, Collections.singletonMap("Url", fileUrl)).getJSONObject("Data");
                JSONObject resultsObject = skinDetectionResult.getJSONObject("Results");
                // 初始化最大值和对应的参数名称
                double maxProbability = 0.0;
                String maxParameter = "";
                // 遍历 JSON 对象中的键值对，找到最大值及其对应的参数名称
                for (String key : resultsObject.keySet()) {
                    double value = resultsObject.getDouble(key);
                    if (value > maxProbability) {
                        maxProbability = value;
                        maxParameter = key;
                    }
                }
                // 输出最大值及其对应的参数名称
                System.out.println("最大概率参数为：" + maxParameter + "，概率值为：" + maxProbability);
                String bodyPart = skinDetectionResult.getString("BodyPart");
                List<String> woundInformationList = new ArrayList<>();
                woundInformationList.add("皮肤问题的可能性:" + maxParameter + ": " + maxProbability);
                woundInformationList.add("皮肤检测部位:" + bodyPart);
                woundInformationLists.add(woundInformationList);
            }

            returnMap.put("woundInformationList", woundInformationLists);
            returnMap.put("status", 0);
            returnMap.put("msg", "上传成功");
            return ResultVOUtil.success(returnMap);
        } catch (Exception e) {
            returnMap.put("status", -1);
            returnMap.put("msg", e.getMessage());
            e.printStackTrace();
            return ResultVOUtil.error(-1, e.getMessage());
        }
    }

    private List<TrustFile> addImg(List<TrustFile> trustFiles) {
        int i = trustFileMapper.afterInsertFile(trustFiles);
        return trustFiles;
    }

    public boolean addFileRela(TrustRelationFile trustRelationFile) {
        String[] fileids = trustRelationFile.getFileId().split(",");
        int insertResult = 0;
        for (String fileid : fileids) {
            TrustRelationFile trustRelationFile1 = new TrustRelationFile();
            trustRelationFile1.setBusiId(trustRelationFile.getBusiId());
            trustRelationFile1.setBusiType(trustRelationFile.getBusiType());
            trustRelationFile1.setPhotoType(trustRelationFile.getPhotoType());
            trustRelationFile1.setFileId(fileid);
            insertResult += trustRelationFileMapper.insertSelective(trustRelationFile1);
        }
        return fileids.length == insertResult;
    }
}
