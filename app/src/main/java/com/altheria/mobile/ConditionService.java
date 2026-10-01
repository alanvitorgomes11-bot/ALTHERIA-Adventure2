package com.altheria.mobile;

import java.util.*;
import java.util.regex.*;

final class ConditionService {
    private static final Pattern NUM = Pattern.compile("^(.+?)(>=|<=|==|>|<)(-?\\d+)$");
    private final AppData data;
    ConditionService(AppData data){this.data=data;}
    boolean matches(String expression, Models.Player p){
        if(expression==null || expression.isBlank() || expression.equalsIgnoreCase("NONE")) return true;
        for(String raw: expression.split("&")){
            String c=raw.trim(); if(c.isEmpty()) continue;
            if(c.startsWith("!flag:")){ if(p.hasFlag(c.substring(6))) return false; continue; }
            if(c.startsWith("flag:")){ if(!p.hasFlag(c.substring(5))) return false; continue; }
            if(c.startsWith("skill:")){ if(!p.hasAbility(c.substring(6))) return false; continue; }
            if(c.startsWith("spell:")){ if(!p.hasSpell(c.substring(6))) return false; continue; }
            if(c.startsWith("nature:")){ if(!p.nature.equalsIgnoreCase(c.substring(7))) return false; continue; }
            if(c.startsWith("life:")){ if(!p.lifePath.name().equalsIgnoreCase(c.substring(5))) return false; continue; }
            if(c.startsWith("realm:")){ if(!p.realm.equalsIgnoreCase(c.substring(6))) return false; continue; }
            if(c.startsWith("city:")){ if(!p.city.equalsIgnoreCase(c.substring(5))) return false; continue; }
            if(c.startsWith("profession:")){ if(!p.profession.equalsIgnoreCase(c.substring(11))) return false; continue; }
            if(c.startsWith("relationStage:")){String part=c.substring(15);String[] a=part.split(":",2);if(a.length!=2)return false;if(!p.relationshipStage(a[0]).equalsIgnoreCase(a[1]))return false;continue;}
            if(c.startsWith("relation:")){String part=c.substring(9);String[] a=part.split(":",2);if(a.length!=2)return false;Matcher m=NUM.matcher(a[1]);if(!m.matches())return false;if(!compare(p.relation(a[0]),m.group(2),Integer.parseInt(m.group(3))))return false;continue;}
            if(c.startsWith("child:")){Matcher m=NUM.matcher(c.substring(6));if(!m.matches())return false;if(!compare(p.childrenCount,m.group(2),Integer.parseInt(m.group(3))))return false;continue;}
            if(c.startsWith("transport:")){String id=c.substring(10); if(!p.ownedTransports.contains(id)) return false; continue;}
            if(c.startsWith("huntIntel")){Matcher m=NUM.matcher(c); if(!m.matches()) return false; if(!compare(p.huntIntel,m.group(2),Integer.parseInt(m.group(3))))return false; continue;}
            if(c.startsWith("cityFlag:")){String part=c.substring(9);String[] a=part.split(":",2);if(a.length!=2)return false;if(!p.hasFlag("city:"+a[0]+":"+a[1])) return false;continue;}
            Matcher m=NUM.matcher(c); if(!m.matches()) continue;
            String key=m.group(1).trim(); int value=Integer.parseInt(m.group(3)); int actual=specialValue(key,p);
            if(!compare(actual,m.group(2),value))return false;
        }
        return true;
    }
    private int specialValue(String key, Models.Player p){
        if(key.startsWith("stat:"))return p.stat(key.substring(5));
        if(key.startsWith("cityThreat:"))return p.cityValue(p.cityThreat,key.substring(11));
        if(key.startsWith("cityEconomy:"))return p.cityValue(p.cityEconomy,key.substring(12));
        if(key.startsWith("cityStability:"))return p.cityValue(p.cityStability,key.substring(14));
        return switch(key){
            case "level"->p.level;case "day"->p.day;case "gold"->p.gold;case "xp"->p.xp;case "reputation"->p.reputation;case "fatigue"->p.fatigue;case "hp"->p.hp;case "mana"->p.mana;case "guildRep"->p.guildRep;case "guildRank"->p.guildRank;case "arenaRating"->p.arenaRating;case "arenaWins"->p.arenaWins;case "arenaLosses"->p.arenaLosses;case "vellum:ARMORIAL"->p.vellumArmorial;case "vellum:MARTIAL"->p.vellumMartial;case "academyDay"->p.academyDay;case "academyYear"->p.academyYear();case "academySemester"->p.academySemester();case "dungeonFloor"->p.dungeonFloor;case "children"->p.childrenCount;case "huntIntel"->p.huntIntel;default->0;};
    }
    static boolean compare(int actual,String op,int expected){return switch(op){case ">="->actual>=expected;case "<="->actual<=expected;case ">"->actual>expected;case "<"->actual<expected;case "=="->actual==expected;default->false;};}
}
