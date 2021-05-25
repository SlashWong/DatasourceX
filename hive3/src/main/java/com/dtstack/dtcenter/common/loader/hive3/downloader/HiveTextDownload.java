package com.dtstack.dtcenter.common.loader.hive3.downloader;

import com.dtstack.dtcenter.common.loader.hadoop.hdfs.HdfsOperator;
import com.dtstack.dtcenter.common.loader.hadoop.util.KerberosLoginUtil;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 下载hive表:存储结构为Text
 * Date: 2020/6/3
 * Company: www.dtstack.com
 * @author wangchuan
 */
@Slf4j
public class HiveTextDownload implements IDownloader {
    private static final int SPLIT_NUM = 1;

    private static final String IMPALA_INSERT_STAGING = "_impala_insert_staging";

    private TextInputFormat inputFormat;
    private JobConf conf;
    private LongWritable key;
    private Text value;

    private int readNum = 0;

    private RecordReader recordReader;
    private String tableLocation;
    private String fieldDelimiter;
    private Configuration configuration;
    private List<String> columnNames;
    
    // 需要查询的字段索引
    private List<Integer> needIndex;

    private List<String> paths;
    private String currFile;
    private int currFileIndex = 0;

    private InputSplit[] splits;
    private int splitIndex = 0;
    private List<String> partitionColumns;
    private Map<String, Object> kerberosConfig;
    /**
     * 按分区下载
     */
    private Map<String, String> filterPartition;

    /**
     * 当前分区的值
     */
    private List<String> currentPartData;

    public HiveTextDownload(Configuration configuration, String tableLocation, List<String> columnNames, String fieldDelimiter,
                            List<String> partitionColumns, Map<String, String> filterPartition, List<Integer> needIndex, Map<String, Object> kerberosConfig){
        this.tableLocation = tableLocation;
        this.columnNames = columnNames;
        this.fieldDelimiter = fieldDelimiter;
        this.partitionColumns = partitionColumns;
        this.configuration = configuration;
        this.filterPartition = filterPartition;
        this.kerberosConfig = kerberosConfig;
        this.needIndex = needIndex;
    }

    @Override
    public boolean configure() throws IOException {

        conf = new JobConf(configuration);
        paths = Lists.newArrayList();
        FileSystem fs =  FileSystem.get(conf);
        // 递归获取表路径下所有文件
        getAllPartitionPath(tableLocation, paths, fs);
        // 有可能表结构还存在metaStore中，但是表路径被删除，但是此时不应该报错
        if(paths.size() == 0){
            return true;
        }
        nextRecordReader();
        key = new LongWritable();
        value = new Text();
        return true;
    }

    /**
     * 递归获取文件夹下所有文件，排除隐藏文件和无关文件
     *
     * @param tableLocation hdfs文件路径
     * @param pathList 所有文件集合
     * @param fs HDFS 文件系统
     */
    public static void getAllPartitionPath(String tableLocation, List<String> pathList, FileSystem fs) throws IOException {
        Path inputPath = new Path(tableLocation);
        // 路径不存在直接返回
        if (!fs.exists(inputPath)) {
            return;
        }
        //剔除隐藏系统文件和无关文件
        FileStatus[] fsStatus = fs.listStatus(inputPath, path -> !path.getName().startsWith(".") && !path.getName().startsWith("_SUCCESS") && !path.getName().startsWith(IMPALA_INSERT_STAGING) && !path.getName().startsWith("_common_metadata"));
        if(fsStatus == null || fsStatus.length == 0){
            return;
        }
        for (FileStatus status : fsStatus) {
            if (status.isFile()) {
                pathList.add(status.getPath().toString());
            }else {
                getAllPartitionPath(status.getPath().toString(), pathList, fs);
            }
        }
    }

    private boolean nextRecordReader() throws IOException {

        if(!nextFile()){
            return false;
        }

        Path inputPath = new Path(currFile);
        inputFormat = new TextInputFormat();

        FileInputFormat.setInputPaths(conf, inputPath);
        TextInputFormat inputFormat = new TextInputFormat();
        inputFormat.configure(conf);
        splits = inputFormat.getSplits(conf, SPLIT_NUM);
        if(splits.length == 0){
            return nextRecordReader();
        }

        if(splits != null && splits.length > 0){
            nextSplitRecordReader();
        }
        return true;
    }

    private boolean nextSplitRecordReader() throws IOException {
        if(splitIndex >= splits.length){
            return false;
        }

        InputSplit fileSplit = splits[splitIndex];
        splitIndex++;

        if(recordReader != null){
            close();
        }

        recordReader = inputFormat.getRecordReader(fileSplit, conf, Reporter.NULL);
        return true;
    }

    private boolean nextFile(){
        if(currFileIndex > (paths.size() - 1)){
            return false;
        }

        currFile = paths.get(currFileIndex);

        if(CollectionUtils.isNotEmpty(partitionColumns)){
            currentPartData = HdfsOperator.parsePartitionDataFromUrl(currFile,partitionColumns);
        }

        if (!isRequiredPartition()){
            currFileIndex++;
            splitIndex = 0;
            nextFile();
        }

        currFileIndex++;
        splitIndex = 0;
        return true;
    }

    public boolean nextRecord() throws IOException {

        if(recordReader.next(key, value)){
            return true;
        }

        //同一个文件夹下是否还存在剩余的split
        while(nextSplitRecordReader()){
            if(nextRecord()){
                return true;
            }
        }

        //查找下一个可读的文件夹
        while (nextRecordReader()){
            if(nextRecord()){
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> getMetaInfo() {
        List<String> metaInfo = new ArrayList<>(columnNames);
        if(CollectionUtils.isNotEmpty(partitionColumns)){
            metaInfo.addAll(partitionColumns);
        }
        return metaInfo;
    }

    @Override
    public List<String> readNext(){
        return KerberosLoginUtil.loginWithUGI(kerberosConfig).doAs(
                (PrivilegedAction<List<String>>) ()->{
                    try {
                        return readNextWithKerberos();
                    } catch (Exception e){
                        throw new DtLoaderException(String.format("Abnormal reading file,%s", e.getMessage()), e);
                    }
                });
    }

    public List<String> readNextWithKerberos(){
        readNum++;
        String line = value.toString();
        value.clear();
        String[] fields = line.split(fieldDelimiter, -1);
        List<String> row = Lists.newArrayList(fields);
        if(CollectionUtils.isNotEmpty(partitionColumns)){
            row.addAll(currentPartData);
        }
        if (CollectionUtils.isNotEmpty(needIndex)) {
            List<String> rowNew = Lists.newArrayList();
            for (Integer index : needIndex) {
                if (index > row.size() -1) {
                    rowNew.add(null);
                } else {
                    rowNew.add(row.get(index));
                }
            }
            return rowNew;
        }
        return row;
    }

    @Override
    public boolean reachedEnd() {
        return KerberosLoginUtil.loginWithUGI(kerberosConfig).doAs(
                (PrivilegedAction<Boolean>) ()->{
                    try {
                        return recordReader == null || !nextRecord();
                    } catch (Exception e){
                        throw new DtLoaderException(String.format("Download file is abnormal,%s", e.getMessage()), e);
                    }
                });
    }

    @Override
    public boolean close() throws IOException {
        if(recordReader != null){
            recordReader.close();
        }
        return true;
    }

    @Override
    public String getFileName() {
        return null;
    }

    /**
     * 判断是否是指定的分区，支持多级分区
     * @return
     */
    private boolean isRequiredPartition(){
        if (filterPartition != null && !filterPartition.isEmpty()) {
            //获取当前路径下的分区信息
            Map<String,String> partColDataMap = new HashMap<>();
            for (String part : currFile.split("/")) {
                if(part.contains("=")){
                    String[] parts = part.split("=");
                    partColDataMap.put(parts[0],parts[1]);
                }
            }

            Set<String> keySet = filterPartition.keySet();
            boolean check = true;
            for (String key : keySet) {
                String partition = partColDataMap.get(key);
                String needPartition = filterPartition.get(key);
                if (!Objects.equals(partition, needPartition)){
                    check = false;
                    break;
                }
            }
            return check;
        }
        return true;
    }

    @Override
    public List<String> getContainers() {
        return Collections.emptyList();
    }

}