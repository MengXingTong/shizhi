package com.shizhi.app;
import java.util.ArrayList;
public final class SpeechChunks {
    public static ArrayList<String> split(String s){ArrayList<String> out=new ArrayList<>();int pos=0;
        while(pos<s.length()){int end=Math.min(pos+160,s.length());if(end<s.length()){int dot=s.lastIndexOf('。',end-1);if(dot>pos+40)end=dot+1;if(end<s.length()&&Character.isHighSurrogate(s.charAt(end-1)))end--;}
            out.add(s.substring(pos,end));pos=end;}return out;}
}