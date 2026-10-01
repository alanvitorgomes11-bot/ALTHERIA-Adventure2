package com.altheria.mobile;

import java.util.*;

final class CombatEngine {
    static final class CombatState {
        final Models.Monster enemy;
        int enemyHp;
        boolean guarding = false;
        final String victoryNext;
        final String victoryEffect;
        final String defeatNext;
        final String defeatEffect;
        final String context;
        final List<String> log = new ArrayList<>();
        CombatState(Models.Monster enemy,String victoryNext,String victoryEffect,String defeatNext,String defeatEffect,String context){
            this.enemy=enemy; this.enemyHp=enemy.hp(); this.victoryNext=victoryNext; this.victoryEffect=victoryEffect;
            this.defeatNext=defeatNext; this.defeatEffect=defeatEffect; this.context=context;
        }
    }

    private final AppData data;
    private final Random rng;
    CombatEngine(AppData data,long seed){this.data=data;this.rng=new Random(seed);}

    void start(GameSession s,String monsterId,String next,String effect,String failNext,String failEffect,String context){
        Models.Monster m=data.monsterById.get(monsterId);
        if(m==null){ s.message="O alvo do combate não foi encontrado."; return; }
        s.combat=new CombatState(m,next,effect,failNext,failEffect,context);
        s.message="Combate iniciado: "+m.name()+".";
        s.combat.log.add("Você enfrenta "+m.name()+" (ameaça "+m.threatClass()+").");
    }

    boolean active(GameSession s){return s.combat!=null;}

    void attack(GameSession s){playerAction(s,"Ataque",0,false,false);}
    void defend(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        c.guarding=true; c.log.add("Você assume uma postura defensiva e procura uma abertura.");
        enemyTurn(s);
    }
    void technique(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        String skill=s.firstCombatSkill();
        int bonus=skill==null?2:4;
        int damage=Math.max(1, s.player.stat("FOR")/2 + s.player.stat("TEC")/3 + s.player.level + bonus + rng.nextInt(6));
        boolean crit=rng.nextInt(100)<10 + s.player.stat("PER"); if(crit)damage*=2;
        c.enemyHp-=damage;
        c.log.add((skill==null?"Você improvisa uma técnica":"Você usa "+s.skillName(skill))+" e causa "+damage+" de dano"+(crit?" — CRÍTICO!":"")+".");
        afterPlayerHit(s);
    }
    void magic(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        String spellId=s.firstKnownSpell();
        if(spellId==null){c.log.add("Você ainda não conhece um feitiço utilizável."); return;}
        Models.Spell sp=data.spellById.get(spellId);
        if(sp==null || s.player.mana<sp.mana()){c.log.add("Mana insuficiente para conjurar."); return;}
        s.player.mana-=sp.mana();
        int damage=Math.max(2, s.player.stat("INT")/2 + s.player.stat("VON")/3 + s.player.level + sp.tier()*3 + rng.nextInt(8));
        c.enemyHp-=damage; c.log.add("Você conjura "+sp.name()+" e causa "+damage+" de dano.");
        afterPlayerHit(s);
    }
    void useItem(GameSession s){
        String id=s.firstHealingItem(); if(id==null){s.combat.log.add("Você não possui um item de cura.");return;}
        Models.Item item=data.itemById.get(id); s.player.removeItem(id,1);
        if(item.heal()>0){int before=s.player.hp;s.player.hp=Math.min(s.player.maxHp,s.player.hp+item.heal());s.combat.log.add("Você usa "+item.name()+": +"+(s.player.hp-before)+" HP.");}
        if(item.mana()>0){int before=s.player.mana;s.player.mana=Math.min(s.player.maxMana,s.player.mana+item.mana());s.combat.log.add("Você recupera "+(s.player.mana-before)+" Mana.");}
        enemyTurn(s);
    }
    void flee(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        int chance=Math.min(90,35+s.player.stat("AGI")*4-c.enemy.threatClass()*7);
        if(rng.nextInt(100)<chance){c.log.add("Você encontra uma abertura e foge do combate.");finishDefeatRoute(s,false);}
        else {c.log.add("A criatura impede sua fuga.");enemyTurn(s);}
    }

    private void playerAction(GameSession s,String label,int flat,boolean magic,boolean item){
        CombatState c=s.combat; if(c==null)return;
        int weapon=s.player.equipmentBonus("ATTACK",data);
        int vellum=s.player.vellumArmorial*2+s.player.vellumMartial*2;
        int damage=Math.max(1,s.player.stat("FOR")/2+s.player.stat("TEC")/3+s.player.level+weapon+vellum+flat+rng.nextInt(6));
        boolean crit=rng.nextInt(100)<7+s.player.stat("PER"); if(crit)damage*=2;
        c.enemyHp-=damage; c.log.add("Você ataca e causa "+damage+" de dano"+(crit?" — CRÍTICO!":"")+"."); afterPlayerHit(s);
    }
    private void afterPlayerHit(GameSession s){
        if(s.combat==null)return;
        if(s.combat.enemyHp<=0){ finishVictory(s); return; }
        enemyTurn(s);
    }
    private void enemyTurn(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        int defense=s.player.stat("VIG")/3+s.player.equipmentBonus("DEFENSE",data)+s.player.vellumArmorial+s.player.vellumMartial;
        int damage=Math.max(1,c.enemy.attack()+c.enemy.threatClass()*2+rng.nextInt(5)-defense/3);
        if(c.guarding){damage=(int)Math.ceil(damage*0.4);c.guarding=false;}
        s.player.hp=Math.max(0,s.player.hp-damage); c.log.add(c.enemy.name()+" causa "+damage+" de dano.");
        if(s.player.hp<=0) finishLoss(s); else s.message="O combate continua.";
    }
    private void finishVictory(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        c.log.add("Vitória! "+c.enemy.name()+" foi derrotado.");
        s.player.addXp(c.enemy.xp()); s.player.gold+=c.enemy.gold(); s.awardAchievement("ACH_0003");
        s.message="Vitória: +"+c.enemy.xp()+" XP, +"+c.enemy.gold()+" ouro."; if(c.enemy.name().toLowerCase().contains("aranha colossal"))s.recordPrologue("EPI_0001"); if(c.enemy.name().toLowerCase().contains("dragão"))s.recordPrologue("EPI_0003");
        s.applyEffects(c.victoryEffect);
        if("ARENA".equals(c.context)){s.finishArenaVictory(); goNext(s,c.victoryNext); return;}
        if("GUILD".equals(c.context)){s.finishGuildQuest(); goNext(s,c.victoryNext); return;}
        if("HUNT".equals(c.context)||"HUNT_RENEGADE".equals(c.context)){s.finishHunt(); goNext(s,c.victoryNext); return;}
        if("DUNGEON".equals(c.context)){
            s.finishDungeonVictory();
            if(!s.player.activeDungeon.isBlank()){ s.advanceTime(); s.combat=null; s.startDungeonFloor(s.data.dungeonById.get(s.player.activeDungeon)); return; }
            goNext(s,c.victoryNext); return;
        }
        goNext(s,c.victoryNext);
    }
    private void finishLoss(GameSession s){
        CombatState c=s.combat; if(c==null)return;
        c.log.add("Você caiu em combate."); s.awardAchievement("ACH_0005"); s.applyEffects(c.defeatEffect); s.player.hp=Math.max(1,s.player.hp); if("DUNGEON".equals(c.context)){s.player.activeDungeon="";s.player.dungeonFloor=0;} if("GUILD".equals(c.context)){s.finishGuildQuest();} s.message="Você sobreviveu por pouco e recuou."; if(c.enemy.name().toLowerCase().contains("aranha colossal"))s.recordPrologue("EPI_0002"); if(c.enemy.name().toLowerCase().contains("dragão"))s.recordPrologue("EPI_0004");
        goNext(s,c.defeatNext);
    }
    private void finishDefeatRoute(GameSession s,boolean apply){
        CombatState c=s.combat; if(c==null)return;
        if(apply)s.applyEffects(c.defeatEffect);
        if("DUNGEON".equals(c.context)){s.player.activeDungeon="";s.player.dungeonFloor=0;}
        if("GUILD".equals(c.context)){s.finishGuildQuest();}
        goNext(s,c.defeatNext);
    }
    private void goNext(GameSession s,String next){
        CombatState c=s.combat; s.combat=null;
        if("END".equals(next)||next==null||next.isBlank()) s.current=s.engine.next(s.player); else s.current=s.data.eventById.get(next);
        s.advanceTime();
    }
}
