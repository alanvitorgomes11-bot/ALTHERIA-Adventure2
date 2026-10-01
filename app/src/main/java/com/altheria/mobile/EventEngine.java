package com.altheria.mobile;

import java.util.*;
import java.util.function.Predicate;

final class EventEngine {
    private final AppData data;
    private final ConditionService conditions;
    private final Random rng;
    EventEngine(AppData data,long seed){this.data=data;this.conditions=new ConditionService(data);this.rng=new Random(seed);}

    Models.Event next(Models.Player p){
        // Active long-form quests, academic progression and nature-exclusive arcs take priority.
        Models.Event priority = pick(p, e -> e.id().startsWith("STORY_") && e.category().equals("CHAIN"));
        if(priority!=null && priority.category().equals("CHAIN")) return priority;
        if(p.lifePath==Models.LifePath.VALARYN){
            Models.Event academic=pick(p,e->e.category().equals("ACADEMIC") && e.requirements().contains("academySemester"));
            if(academic!=null && rng.nextInt(100)<45) return academic;
        }
        if(!p.nature.isBlank()){
            Models.Event nature=pick(p,e->e.category().equals("ELYTH") && e.requirements().contains("nature:"+p.nature));
            if(nature!=null && rng.nextInt(100)<25) return nature;
        }
        List<Models.Event> pool=data.events.stream().filter(e->eligible(e,p)).toList();
        if(pool.isEmpty()){
            Models.Event fallback=data.events.stream()
                .filter(e->e.location().equals("ANY"))
                .filter(e->eligibleIgnoringLocation(e,p))
                .findFirst().orElseGet(()->data.events.isEmpty()?null:data.events.get(0));
            return fallback;
        }
        return weighted(pool,p);
    }

    Models.Event nextByCategory(Models.Player p,String category){
        Models.Event e=pick(p,x->x.category().equals(category));
        return e!=null?e:next(p);
    }

    private Models.Event pick(Models.Player p,Predicate<Models.Event> selector){
        List<Models.Event> pool=data.events.stream().filter(selector).filter(e->eligible(e,p)).toList();
        return pool.isEmpty()?null:weighted(pool,p);
    }

    private Models.Event weighted(List<Models.Event> pool,Models.Player p){
        int total=pool.stream().mapToInt(e->Math.max(1,dynamicWeight(e,p))).sum();
        int pick=rng.nextInt(Math.max(1,total));
        for(Models.Event e:pool){pick-=Math.max(1,dynamicWeight(e,p));if(pick<0)return e;}
        return pool.get(pool.size()-1);
    }

    private boolean eligibleIgnoringLocation(Models.Event e,Models.Player p){
        if(p.level<e.minLevel()||p.day<e.minDay())return false;
        if(!e.lifePath().equals("ANY")&&!e.lifePath().equals(p.lifePath.name()))return false;
        if(!e.realm().equals("ANY")&&!e.realm().equals(p.realm))return false;
        if(!conditions.matches(e.requirements(),p))return false;
        if(e.rarity().equals("UNIQUE")&&p.seenEvents.contains(e.id()))return false;
        int last=p.eventHistory.getOrDefault(e.id(),-99999);
        return e.cooldown()<=0||p.day-last>=e.cooldown();
    }

    Models.Event byId(String id){return data.eventById.get(id);}
    boolean choiceAllowed(Models.Choice c,Models.Player p){return conditions.matches(c.requirement(),p);}

    boolean eligible(Models.Event e,Models.Player p){
        if(p.level<e.minLevel()||p.day<e.minDay())return false;
        if(!e.lifePath().equals("ANY")&&!e.lifePath().equals(p.lifePath.name()))return false;
        if(!e.realm().equals("ANY")&&!e.realm().equals(p.realm))return false;
        if(!e.location().equals("ANY")&&!e.location().isBlank()&&!e.location().equals(p.city))return false;
        if(!conditions.matches(e.requirements(),p))return false;
        if(e.rarity().equals("UNIQUE")&&p.seenEvents.contains(e.id()))return false;
        int last=p.eventHistory.getOrDefault(e.id(),-99999);
        if(e.cooldown()>0&&p.day-last<e.cooldown())return false;
        return true;
    }

    private int dynamicWeight(Models.Event e,Models.Player p){
        int w=Math.max(1,e.weight());
        if(e.location().equals(p.city))w*=3;
        int cityThreat=p.cityValue(p.cityThreat,p.city), economy=p.cityValue(p.cityEconomy,p.city), stability=p.cityValue(p.cityStability,p.city);
        if(e.category().equals("CHAIN"))w+=12;
        if(e.category().equals("ELYTH"))w+=p.level>=3?8:2;
        if(e.category().equals("ACADEMIC"))w+=20;
        if(e.category().equals("GUILD")&&p.guildRep>=3)w+=10;
        if(e.category().equals("SOCIAL")&&p.reputation>=5)w+=8;
        if(e.category().equals("CITY"))w+=Math.max(0,4-stability)+Math.max(0,cityThreat)+Math.max(0,-economy);
        if(e.category().equals("OPPORTUNITY"))w+=Math.max(0,economy*2+stability);
        if(e.category().equals("COMBAT")&&p.level>=e.minLevel()+2)w=Math.max(1,w/2);
        return Math.max(1,w);
    }
}
