package com.altheria.mobile;

import java.util.*;

final class GameSession {
    final AppData data;
    final EventEngine engine;
    final CombatEngine combatEngine;
    final Random rng;
    Models.Player player;
    Models.Event current;
    CombatEngine.CombatState combat;
    String message="";
    String activeQuestId="";
    int activeQuestRewardXp=0, activeQuestRewardGold=0;
    String activeQuestTarget="";

    GameSession(AppData data){this(data,new Random().nextLong());}
    GameSession(AppData data,long seed){this.data=data;this.rng=new Random(seed);this.engine=new EventEngine(data,seed^0x51A77A);this.combatEngine=new CombatEngine(data,seed^0xC0B471);}
    GameSession(AppData data,Models.Player p){this(data);this.player=p;}

    void startNew(String name,Models.LifePath path,String realm,String city,String profession,String background,int age){
        player=new Models.Player();player.name=name==null||name.isBlank()?"Aventureiro":name.trim();player.lifePath=path;player.realm=realm;player.city=city;player.profession=profession;player.background=background;player.age=age;
        player.discoveredCities.add(city);
        for(Models.Npc n:data.npcs) if(n.realm().equals(realm) && n.city().equals(city)) player.relations.putIfAbsent(n.id(),n.startingRelation());
        player.addItem("ITEM_BREAD",3);player.addItem("ITEM_POTION",1);player.addItem("ITEM_ROPE",1);
        if(path==Models.LifePath.VALARYN){
            player.nature=data.natureNames.get(rng.nextInt(data.natureNames.size()));player.vellumArmorial=1;player.flags.add("ritual_despertar_concluido");player.flags.add("academia_ingresso");grantStarterNatureSkill();
        }else if(path==Models.LifePath.ADVENTURER){player.vellumArmorial=1;grantFirstArmorialSkill();}
        else {player.vellumMartial=1;player.flags.add("magic_tier_1");grantFirstMartialSkill();}
        current=engine.next(player); syncCurrentEvent(); message="A jornada começou. Sua primeira decisão já pode alterar o futuro.";awardAchievement("ACH_0001");
    }

    void choose(int which){
        if(player==null||current==null||combat!=null)return;
        Models.Choice c=which==1?current.choice1():current.choice2(); if(c==null)return;
        boolean ok=engine.choiceAllowed(c,player);player.seenEvents.add(current.id());player.eventHistory.put(current.id(),player.day);
        if(ok){
            if(c.combatMonster()!=null&&!c.combatMonster().isBlank()){combatEngine.start(this,c.combatMonster(),c.next(),c.successEffect(),c.failNext(),c.failEffect(),"EVENT");message+=" Combate provocado pela sua decisão.";return;}
            applyEffects(c.successEffect());goNext(c.next());message="Decisão tomada. O mundo reage às suas ações.";
        }else{applyEffects(c.failEffect());goNext(c.failNext());message="Você não atende aos requisitos dessa escolha. A situação tomou outro rumo.";}
        advanceTime();syncCurrentEvent();
    }
    private void goNext(String next){current=(next==null||next.isBlank()||next.equals("END")||next.equals("COMBAT"))?engine.next(player):engine.byId(next);if(current==null)current=engine.next(player);syncCurrentEvent();}
    private void syncCurrentEvent(){if(player!=null&&current!=null)player.currentEventId=current.id();}

    void advanceTime(){
        int before=player.academyDay;player.day++;player.fatigue=Math.min(100,player.fatigue+1);
        decayNeeds(8,12);
        if(player.day>=120)player.flags.add("era_fronteira");
        if(player.day>=240)player.flags.add("era_tensao");
        if(player.day>=360)player.flags.add("era_crise");
        if(player.day>=480)player.flags.add("era_mobilizacao");
        if(player.day>=600)player.flags.add("era_guerra");
        if(player.day>=700)player.flags.add("era_despertar");
        if(player.day>=760)player.flags.add("era_ultima_campanha");
        if(player.day>=820)player.flags.add("era_dragao");
        if(player.hunger<=25||player.thirst<=25)player.fatigue=Math.min(100,player.fatigue+1);
        if(player.hunger<=10||player.thirst<=10)player.hp=Math.max(1,player.hp-1);
        if(player.lifePath==Models.LifePath.VALARYN){player.academyDay++;autoAcademicProgress(before,player.academyDay);} 
        if(player.fatigue>=15)player.hp=Math.max(1,player.hp-1);
        if(player.maxMana>0&&player.day%3==0)player.mana=Math.min(player.maxMana,player.mana+1);
    }
    private void autoAcademicProgress(int before,int now){int[]marks={183,365,548,730,913,1095};String[]flags={"academia_sem1_concluido","academia_ano_1_concluido","academia_sem3_concluido","academia_ano_2_concluido","academia_sem5_concluido","academia_formado"};for(int i=0;i<marks.length;i++)if(before<marks[i]&&now>=marks[i])player.flags.add(flags[i]);if(before<1095&&now>=1095){player.flags.add("academia_formado");player.vellumArmorial=Math.max(1,player.vellumArmorial);message="Você completou os três anos da formação acadêmica: agora é um Valaryn Cadete (F).";}}
    void rest(){player.fatigue=Math.max(0,player.fatigue-8);player.hp=Math.min(player.maxHp,player.hp+10);if(player.maxMana>0)player.mana=Math.min(player.maxMana,player.mana+8);advanceTime();message="Você descansou e passou um dia se recuperando. Lembre-se de comer e beber antes de partir.";current=engine.next(player);syncCurrentEvent();}
    private void decayNeeds(int hungerLoss,int thirstLoss){player.hunger=Math.max(0,player.hunger-hungerLoss);player.thirst=Math.max(0,player.thirst-thirstLoss);}
    private void restoreNeeds(Models.Item item){player.hunger=Math.min(100,player.hunger+Math.max(0,item.hunger()));player.thirst=Math.min(100,player.thirst+Math.max(0,item.thirst()));}

    void applyEffects(String effectStr){
        if(effectStr==null||effectStr.isBlank()||effectStr.equals("NONE"))return;
        for(String part:effectStr.split(";")){String[]kv=part.split("=",2);if(kv.length!=2)continue;String k=kv[0].trim(),v=kv[1].trim();try{
            if(k.equals("xp")){player.addXp(Integer.parseInt(v));syncProgression();continue;} if(k.equals("gold")){player.gold=Math.max(0,player.gold+Integer.parseInt(v));continue;} if(k.equals("reputation")){player.reputation+=Integer.parseInt(v);continue;} if(k.equals("fatigue")){player.fatigue=Math.max(0,Math.min(100,player.fatigue+Integer.parseInt(v)));continue;} if(k.equals("hp")){player.hp=Math.max(0,Math.min(player.maxHp,player.hp+Integer.parseInt(v)));continue;} if(k.equals("mana")){player.mana=Math.max(0,Math.min(player.maxMana,player.mana+Integer.parseInt(v)));continue;} if(k.equals("days")){int d=Integer.parseInt(v);player.day+=d;decayNeeds(Math.max(1,d*8),Math.max(1,d*12));if(player.lifePath==Models.LifePath.VALARYN)player.academyDay+=d;continue;} if(k.equals("guildRep")){player.guildRep=Math.max(0,player.guildRep+Integer.parseInt(v));guildPromotionCheck();continue;} if(k.equals("guildRank")){player.guildRank=Math.max(player.guildRank,Integer.parseInt(v));continue;} if(k.equals("arenaRating")){player.arenaRating=Math.max(0,player.arenaRating+Integer.parseInt(v));continue;} if(k.equals("arenaWins")){player.arenaWins+=Integer.parseInt(v);continue;} if(k.equals("arenaLosses")){player.arenaLosses+=Integer.parseInt(v);continue;}
            if(k.startsWith("stat:")){player.statAdd(k.substring(5),Integer.parseInt(v));continue;} if(k.startsWith("rel:")){String id=k.substring(4);player.relationAdd(id,Integer.parseInt(v));refreshRelationshipStage(id);continue;} if(k.startsWith("item:")){int n=Integer.parseInt(v);if(n>0)player.addItem(k.substring(5),n);else player.removeItem(k.substring(5),-n);continue;} if(k.startsWith("removeItem:")){player.removeItem(k.substring(11),Math.abs(Integer.parseInt(v)));continue;} if(k.startsWith("skill:")){if(Integer.parseInt(v)>0)player.abilities.add(k.substring(6));else player.abilities.remove(k.substring(6));continue;} if(k.startsWith("spell:")){if(Integer.parseInt(v)>0){player.spells.add(k.substring(6));Models.Spell s=data.spellById.get(k.substring(6));if(s!=null){player.maxMana=Math.max(player.maxMana,30+s.tier()*4);player.mana=player.maxMana;}}else player.spells.remove(k.substring(6));continue;} if(k.startsWith("flag:")){if(!v.equalsIgnoreCase("false"))player.flags.add(k.substring(5));else player.flags.remove(k.substring(5));continue;} if(k.equals("prologue")){recordPrologue(v);continue;} if(k.startsWith("unflag:")){player.flags.remove(k.substring(7));continue;}
            if(k.startsWith("cityStability:")){player.cityAdd(player.cityStability,k.substring(14),Integer.parseInt(v));continue;} if(k.startsWith("cityEconomy:")){player.cityAdd(player.cityEconomy,k.substring(12),Integer.parseInt(v));continue;} if(k.startsWith("cityThreat:")){player.cityAdd(player.cityThreat,k.substring(11),Integer.parseInt(v));continue;} if(k.equals("vellum:ARMORIAL")){player.vellumArmorial=Math.max(0,Math.min(7,player.vellumArmorial+Integer.parseInt(v)));syncVellumSkills();continue;} if(k.equals("vellum:MARTIAL")){player.vellumMartial=Math.max(0,Math.min(7,player.vellumMartial+Integer.parseInt(v)));syncVellumSkills();continue;} if(k.startsWith("discover:")){player.discoveredCities.add(k.substring(9));continue;} if(k.startsWith("equip:")){player.equipment.put(k.substring(6),v);continue;}
        }catch(NumberFormatException ignored){}}
    }

    void syncProgression(){if(player.level>=2&&player.lifePath==Models.LifePath.VALARYN)grantStarterNatureSkill();if(player.level>=2&&player.vellumArmorial>0)grantFirstArmorialSkill();if(player.level>=2&&player.vellumMartial>0)grantFirstMartialSkill();}
    private void grantStarterNatureSkill(){if(player.nature.isBlank())return;data.elythAbilities.stream().filter(a->a.nature().equals(player.nature)&&a.tier()<=Math.min(7,player.level)).findFirst().ifPresent(a->player.abilities.add(a.id()));}
    private void grantFirstArmorialSkill(){data.armorialAbilities.stream().filter(a->a.tier()<=player.vellumArmorial).findFirst().ifPresent(a->player.abilities.add(a.id()));}
    private void grantFirstMartialSkill(){data.martialAbilities.stream().filter(a->a.tier()<=player.vellumMartial).findFirst().ifPresent(a->player.abilities.add(a.id()));}
    private void syncVellumSkills(){grantFirstArmorialSkill();grantFirstMartialSkill();}
    List<Models.Ability> learnableSkills(){List<Models.Ability> out=new ArrayList<>();if(!player.nature.isBlank())out.addAll(data.elythAbilities.stream().filter(a->a.nature().equals(player.nature)&&a.tier()<=7&&!player.abilities.contains(a.id())&&skillPrerequisiteMet(a.id())).limit(18).toList());out.addAll(data.armorialAbilities.stream().filter(a->a.tier()<=player.vellumArmorial&&!player.abilities.contains(a.id())&&skillPrerequisiteMet(a.id())).limit(9).toList());out.addAll(data.martialAbilities.stream().filter(a->a.tier()<=player.vellumMartial&&!player.abilities.contains(a.id())&&skillPrerequisiteMet(a.id())).limit(9).toList());return out;}
    boolean learnSkill(String id){if(player.skillPoints<=0||player.abilities.contains(id))return false;Models.Ability a=data.abilityById.get(id);if(a==null)a=allVellumAbility(id);if(a==null||a.tier()>7||!skillPrerequisiteMet(id))return false;if(id.startsWith("ARM_")&&player.vellumArmorial<a.tier())return false;if(id.startsWith("MAR_")&&player.vellumMartial<a.tier())return false;if(a.nature()!=null&&!a.nature().equals("VELLUM")&&!a.nature().equals(player.nature))return false;player.abilities.add(id);player.skillPoints--;return true;}
    boolean skillPrerequisiteMet(String id){String pre=data.prerequisiteById.getOrDefault(id,"");return pre.isBlank()||player.abilities.contains(pre);}
    String skillPrerequisite(String id){return data.prerequisiteById.getOrDefault(id,"");}
    private Models.Ability allVellumAbility(String id){for(Models.Ability a:data.armorialAbilities)if(a.id().equals(id))return a;for(Models.Ability a:data.martialAbilities)if(a.id().equals(id))return a;return null;}
    boolean studyGrimoire(int tier){ if(tier<1||tier>10)return false; if(player.level<1+(tier-1)*4){message="Seu nível ainda não permite estudar esse círculo.";return false;} int cost=20+tier*35; if(player.gold<cost){message="Você precisa de "+cost+" ouro para acesso, cópias e materiais de estudo.";return false;} player.gold-=cost; player.flags.add("magic_tier_"+tier); player.day+=2+tier; player.fatigue=Math.min(100,player.fatigue+2); message="Você estudou um grimório do "+tier+"º círculo. Os feitiços desse nível agora podem ser aprendidos se os demais requisitos forem atendidos."; return true;}
    List<Models.Spell> learnableSpells(){return data.spells.stream().filter(sp->!player.spells.contains(sp.id())&&spellPrerequisiteMet(sp.id())&&player.skillPoints>0&&sp.tier()<=10&&player.level>=1+(sp.tier()-1)*4&&(sp.tier()==1||player.hasFlag("magic_tier_"+sp.tier()))).limit(20).toList();}
    boolean learnSpell(String id){if(player.skillPoints<=0||player.spells.contains(id))return false;Models.Spell sp=data.spellById.get(id);if(sp==null||sp.tier()>10||!spellPrerequisiteMet(id)||player.level<1+(sp.tier()-1)*4||(sp.tier()>1&&!player.hasFlag("magic_tier_"+sp.tier())))return false;player.spells.add(id);player.skillPoints--;player.maxMana=Math.max(player.maxMana,30+sp.tier()*4);player.mana=Math.min(player.maxMana,player.mana+sp.tier()*4);return true;}
    boolean spellPrerequisiteMet(String id){String pre=data.prerequisiteById.getOrDefault(id,"");return pre.isBlank()||player.spells.contains(pre);}
    String firstCombatSkill(){return player.abilities.stream().filter(id->data.abilityById.containsKey(id)||data.armorialAbilities.stream().anyMatch(a->a.id().equals(id))||data.martialAbilities.stream().anyMatch(a->a.id().equals(id))).findFirst().orElse(null);}
    String skillName(String id){if(data.abilityById.containsKey(id))return data.abilityById.get(id).name();for(Models.Ability a:data.armorialAbilities)if(a.id().equals(id))return a.name();for(Models.Ability a:data.martialAbilities)if(a.id().equals(id))return a.name();return id;}
    String firstKnownSpell(){return player.spells.stream().findFirst().orElse(null);} String firstHealingItem(){for(String id:player.inventory.keySet()){Models.Item i=data.itemById.get(id);if(i!=null&&(i.heal()>0||i.mana()>0))return id;}return null;}

    void travelTo(String to){
        Models.Route best=data.routes.stream().filter(r->(r.from().equals(player.city)&&r.to().equals(to))||(r.to().equals(player.city)&&r.from().equals(to))).findFirst().orElse(null);if(best==null){message="Não existe uma rota registrada para esse destino.";return;}
        Models.Transport tr=data.transportById.get(player.activeTransportId);double speed=tr==null?1.0:tr.speed();int days=Math.max(1,(int)Math.ceil(best.days()/speed));int cost=Math.max(1,(int)Math.ceil(best.cost()*(tr==null?1.0:0.88)));if(player.gold<cost){message="O custo da viagem é maior que seu ouro disponível.";return;}
        String origin=player.city;player.gold-=cost;player.city=to;player.discoveredCities.add(to);player.day+=days;player.fatigue=Math.min(100,player.fatigue+days*2);int risk=Math.max(1,best.risk()-(tr==null?0:tr.riskModifier()));message="Você viajou até "+to+" em "+days+" dia(s)"+(tr==null?".":" usando "+tr.name()+".");
        if(rng.nextInt(100)<Math.min(80,risk+player.cityValue(player.cityThreat,origin)+player.cityValue(player.cityThreat,to))){player.flags.add("travel_incident");message+=" A rota trouxe um incidente inesperado.";current=engine.nextByCategory(player,"EXPLORATION");}else current=engine.next(player);syncCurrentEvent();
    }
    List<Models.Route> nearbyRoutes(){List<Models.Route>out=new ArrayList<>();for(Models.Route r:data.routes)if(r.from().equals(player.city)||r.to().equals(player.city))out.add(r);return out;}

    List<Models.Transport> transportOptions(){return data.transports.stream().filter(t->t.realm().equals("ANY")||t.realm().equals(player.realm)).filter(t->!player.ownedTransports.contains(t.id())).sorted(Comparator.comparingInt(Models.Transport::cost)).limit(20).toList();}
    boolean buyTransport(String id){Models.Transport t=data.transportById.get(id);if(t==null||player.ownedTransports.contains(id)||player.gold<t.cost())return false;player.gold-=t.cost();player.ownedTransports.add(id);player.activeTransportId=id;message="Você adquiriu "+t.name()+" e passou a usá-lo nas viagens.";return true;}
    boolean setTransport(String id){if(id==null||id.isBlank()){player.activeTransportId="";message="Você voltou a viajar sem montaria/veículo.";return true;}if(!player.ownedTransports.contains(id))return false;player.activeTransportId=id;message="Transporte ativo: "+data.transportById.get(id).name();return true;}

    // Guild: legacy offers remain, while the new hunt board requires intel.
    List<Models.Monster> guildOffers(){int maxThreat=Math.min(9,Math.max(1,player.guildRank+2));return data.monsters.stream().filter(m->m.realm().equals("ANY")||m.realm().equals(player.realm)).filter(m->m.threatClass()<=maxThreat).sorted(Comparator.comparingInt(Models.Monster::threatClass)).limit(8).toList();}
    List<Models.Renegade> guildRenegadeOffers(){int maxIdx=Math.min(7,Math.max(0,player.guildRank+1));return data.renegades.stream().filter(r->r.realm().equals(player.realm)&&rankIndex(r.rank())<=maxIdx&&r.status().equals("MAPPED")).sorted(Comparator.comparingInt(Models.Renegade::threat)).limit(12).toList();}
    void acceptGuildQuest(Models.Monster m){activeQuestId="QUEST_"+m.id()+"_"+player.day;activeQuestTarget=m.id();activeQuestRewardXp=10+m.threatClass()*8;activeQuestRewardGold=15+m.threatClass()*12;message="Quest aceita: eliminar/estudar "+m.name()+". Recompensa: "+activeQuestRewardXp+" XP + "+activeQuestRewardGold+" ouro.";combatEngine.start(this,m.id(),"END","xp="+activeQuestRewardXp+";gold="+activeQuestRewardGold+";guildRep=1;reputation=1","END","reputation=-1","GUILD");}
    void acceptMonsterHunt(Models.Monster m,String mode){player.activeHuntType="MONSTER";player.activeHuntTargetId=m.id();player.activeHuntMode=mode;player.huntIntel=0;player.huntIntelRequired=Math.max(1,2+m.threatClass()/3);message="Caçada registrada. Primeiro reúna informações sobre "+m.name()+".";}
    void acceptRenegadeHunt(Models.Renegade r,String mode){player.activeHuntType="RENEGADE";player.activeHuntTargetId=r.id();player.activeHuntMode=mode;player.huntIntel=0;player.huntIntelRequired=r.intelRequired();message="Alvo mapeado: "+r.name()+" — Rank "+r.rank()+". Local aproximado: "+r.approxLocation()+". Reúna inteligência antes de partir.";}
    boolean hasActiveHunt(){return !player.activeHuntType.isBlank()&&!player.activeHuntTargetId.isBlank();}
    String activeHuntSummary(){if(!hasActiveHunt())return "Nenhuma caçada ativa.";if(player.activeHuntType.equals("MONSTER")){Models.Monster m=data.monsterById.get(player.activeHuntTargetId);return "Monstro: "+(m==null?player.activeHuntTargetId:m.name())+" | inteligência "+player.huntIntel+"/"+player.huntIntelRequired+" | modo "+player.activeHuntMode;}Models.Renegade r=data.renegadeById.get(player.activeHuntTargetId);return "Renegado: "+(r==null?player.activeHuntTargetId:r.name())+" | Rank "+(r==null?"?":r.rank())+" | inteligência "+player.huntIntel+"/"+player.huntIntelRequired+" | "+(r==null?"":r.approxLocation())+" | modo "+player.activeHuntMode;}
    void gatherHuntIntel(){if(!hasActiveHunt()){message="Nenhuma caçada ativa.";return;}int score=1;if(player.stat("PER")>=8)score++;if(player.stat("INT")>=8)score++;if(player.stat("PRE")>=8)score++;if(player.gold<3){message="Você precisa de pelo menos 3 moedas para pagar informantes e buscas.";return;}player.gold-=3;player.huntIntel=Math.min(player.huntIntelRequired,player.huntIntel+score);player.day++;player.fatigue=Math.min(100,player.fatigue+1);message="Você reuniu pistas. Inteligência: "+player.huntIntel+"/"+player.huntIntelRequired+".";if(player.huntIntel>=player.huntIntelRequired)message+=" O alvo pode ser localizado.";}
    boolean launchActiveHunt(){if(!hasActiveHunt()){message="Nenhuma caçada ativa.";return false;}if(player.huntIntel<player.huntIntelRequired){message="Ainda faltam pistas. Reúna mais inteligência antes de partir.";return false;}
        String next="END";if(player.activeHuntType.equals("MONSTER")){Models.Monster m=data.monsterById.get(player.activeHuntTargetId);if(m==null)return false;combatEngine.start(this,m.id(),next,"xp="+(20+m.threatClass()*10)+";gold="+(20+m.threatClass()*12)+";guildRep=1;reputation=1","END","reputation=-1","HUNT");}
        else{Models.Renegade r=data.renegadeById.get(player.activeHuntTargetId);if(r==null)return false;Models.Monster phantom=new Models.Monster("HUNT_REN","Renegado — "+r.name(),r.realm(),r.threat(),r.primaryPath(),r.powerSummary(),40+r.threat()*24,6+r.threat()*5,4+r.threat()*4,0,0,"esconderijo", "informação");data.monsterById.put(phantom.id(),phantom);combatEngine.start(this,phantom.id(),next,"NONE","END","reputation=-2","HUNT_RENEGADE");}
        message="Você localizou o alvo. A caçada começou.";return true;}
    void finishHunt(){if(!hasActiveHunt())return; if(player.activeHuntType.equals("RENEGADE")){Models.Renegade r=data.renegadeById.get(player.activeHuntTargetId);if(r!=null){if(player.activeHuntMode.equals("CAPTURE")){player.gold+=r.captureReward();player.addXp(25+r.threat()*12);}else{player.gold+=r.eliminationReward();player.addXp(35+r.threat()*15);}player.guildRep+=1;guildPromotionCheck();player.flags.add("renegade_hunt_"+r.id()+"_resolved");}} else {player.completedQuests.add("HUNT_"+player.activeHuntTargetId+"_"+player.day);}
        player.activeHuntType="";player.activeHuntTargetId="";player.activeHuntMode="";player.huntIntel=0;player.huntIntelRequired=0;message="Caçada concluída. Recompensa registrada e a Guilda atualizou seus antecedentes.";
    }
    int rankIndex(String rank){return switch(rank){case "F"->0;case "E"->1;case "D"->2;case "C"->3;case "B"->4;case "A"->5;case "S"->6;case "Z"->7;default->0;};}
    void guildPromotionCheck(){while(player.guildRep>=5&&player.guildRank<7){player.guildRep-=5;player.guildRank++;player.flags.add("guild_rank_"+player.guildRank);message="A Guilda reconheceu seu avanço. Novo nível de reputação: "+player.guildRank+".";}}

    void enterDungeon(Models.Dungeon d){if(player.gold<d.entryCost()){message="Você não tem ouro para pagar a entrada.";return;}player.gold-=d.entryCost();player.activeDungeon=d.id();player.dungeonFloor=1;startDungeonFloor(d);}
    void startDungeonFloor(Models.Dungeon d){int targetThreat=Math.min(d.maxThreat(),Math.max(d.minThreat(),Math.min(9,player.level/2+player.dungeonFloor)));List<Models.Monster>pool=data.monsters.stream().filter(x->x.threatClass()<=targetThreat).toList();Models.Monster m=pool.get(rng.nextInt(pool.size()));combatEngine.start(this,m.id(),"END","gold="+(10+player.dungeonFloor*5)+";xp="+(8+player.dungeonFloor*4),"END","fatigue=3","DUNGEON");}
    void finishDungeonVictory(){Models.Dungeon d=data.dungeonById.get(player.activeDungeon);if(d==null)return;if(player.dungeonFloor>=d.maxFloor()){message="Você conquistou o último andar de "+d.name()+".";player.gold+=d.maxFloor()*8;player.activeDungeon="";player.dungeonFloor=0;}else{player.dungeonFloor++;message="Andar "+player.dungeonFloor+" alcançado. Os riscos aumentaram.";}}
    void startArena(){int targetThreat=Math.min(9,Math.max(1,1+(player.arenaRating-1000)/150+player.level/3));Models.Monster m=new Models.Monster("ARENA","Combatente da Arena",player.realm,Math.max(1,targetThreat),"VELLUM","Luta técnica e adaptativa.",28+player.level*7,targetThreat*3+5,targetThreat*2+4,18+targetThreat*4,25+targetThreat*8,"Arena","");data.monsterById.put(m.id(),m);combatEngine.start(this,m.id(),"END","arenaRating=25;arenaWins=1;guildRep=1;reputation=1","END","arenaRating=-30;arenaLosses=1","ARENA");}
    void finishArenaVictory(){message="Vitória na Arena. Sua reputação competitiva aumentou.";guildPromotionCheck();}
    void finishGuildQuest(){if(activeQuestId.isBlank())return;player.completedQuests.add(activeQuestId);player.addItem("ITEM_GUILD_TOKEN",1);activeQuestId="";activeQuestTarget="";activeQuestRewardXp=0;activeQuestRewardGold=0;guildPromotionCheck();}

    boolean useItem(String id){Models.Item item=data.itemById.get(id);if(item==null||player.itemCount(id)<=0||!item.consumable())return false;player.removeItem(id,1);if(item.heal()>0)player.hp=Math.min(player.maxHp,player.hp+item.heal());if(item.mana()>0&&player.maxMana>0)player.mana=Math.min(player.maxMana,player.mana+item.mana());restoreNeeds(item);if(id.equals("ITEM_BREAD"))player.fatigue=Math.max(0,player.fatigue-2);message="Você usou "+item.name()+".";return true;}
    boolean equipItem(String id){Models.Item item=data.itemById.get(id);if(item==null||item.slot().isBlank()||player.itemCount(id)<=0)return false;player.equipment.put(item.slot(),id);message="Equipado: "+item.name()+".";return true;}

    boolean recruitPartyMember(String npcId){ Models.Npc n=data.npcById.get(npcId); if(n==null||!n.city().equals(player.city)||player.partyMembers.size()>=4)return false; if(player.gold<8){message="Você precisa de 8 ouro para despesas iniciais do grupo.";return false;} player.gold-=8;player.partyMembers.add(npcId);player.relationAdd(npcId,3);message=n.name()+" aceitou viajar com você. Grupo: "+player.partyMembers.size()+"/4.";return true;}
    void dismissPartyMember(String npcId){player.partyMembers.remove(npcId);message="O grupo foi reorganizado.";}
    List<Models.Npc> nearbyNpcs(){List<Models.Npc>all=data.npcs.stream().filter(n->n.city().equals(player.city)&&n.status().equals("ACTIVE")).toList();if(all.size()<=30)return all;int start=Math.floorMod(player.day,all.size());List<Models.Npc>out=new ArrayList<>();for(int i=0;i<30;i++)out.add(all.get((start+i)%all.size()));return out;}
    List<Models.Npc> knownFriends(){return data.npcs.stream().filter(n->player.relation(n.id())>=10||Set.of("AMIGO","PROXIMO","ROMANCE","PARCEIRO","CASADO").contains(player.relationshipStage(n.id()))).limit(40).toList();}
    void talkToNpc(String npcId){Models.Npc n=data.npcById.get(npcId);if(n==null){message="Pessoa não encontrada.";return;}if(!n.city().equals(player.city)){message="Essa pessoa não está nesta cidade. Você pode procurá-la pela aba Relações.";return;}player.relations.putIfAbsent(npcId,n.startingRelation());player.relationAdd(npcId,2);player.reputation+=1;player.day++;player.fatigue=Math.min(100,player.fatigue+1);refreshRelationshipStage(npcId);message="Você conversou com "+n.name()+". Relação: "+player.relation(npcId)+" ("+player.relationshipStage(npcId)+").";current=engine.next(player);syncCurrentEvent();}
    void refreshRelationshipStage(String npcId){int r=player.relation(npcId);String cur=player.relationshipStage(npcId);if(cur.equals("CASADO"))return;if(r>=60&&cur.equals("ROMANCE"))player.setRelationshipStage(npcId,"PARCEIRO");else if(r>=45&&cur.equals("PROXIMO"))player.setRelationshipStage(npcId,"ROMANCE");else if(r>=25)player.setRelationshipStage(npcId,"PROXIMO");else if(r>=10)player.setRelationshipStage(npcId,"AMIGO");}
    boolean befriendNpc(String id){Models.Npc n=data.npcById.get(id);if(n==null||!n.city().equals(player.city))return false;player.relationAdd(id,5);player.setRelationshipStage(id,"AMIGO");player.flags.add("friend_"+id);message="Vocês começaram uma amizade. "+n.name()+" agora faz parte da sua lista de amigos.";return true;}
    boolean startRomance(String id){Models.Npc n=data.npcById.get(id);if(n==null||n.age()<18||player.age<18||player.relation(id)<45)return false;player.setRelationshipStage(id,"ROMANCE");player.flags.add("romance_"+id);message="A relação com "+n.name()+" passou a um vínculo romântico.";return true;}
    boolean marryNpc(String id){Models.Npc n=data.npcById.get(id);if(n==null||n.age()<18||player.age<18||player.relation(id)<75||!Set.of("ROMANCE","PARCEIRO").contains(player.relationshipStage(id)))return false;player.setRelationshipStage(id,"CASADO");player.spouseId=id;player.flags.add("married_"+id);message="Você e "+n.name()+" decidiram se casar. Esse vínculo agora faz parte da história do personagem.";return true;}
    boolean expandFamily(){if(player.spouseId.isBlank()||!player.relationshipStage(player.spouseId).equals("CASADO"))return false;Models.Npc spouse=data.npcById.get(player.spouseId);String base=spouse==null?"Filho":spouse.name();String child=base+" Jr."+(player.childrenCount+1);player.childrenCount++;player.childrenNames.add(child);player.flags.add("child_"+player.childrenCount);message="Sua família cresceu. Nasceu "+child+".";return true;}

    void awardAchievement(String id){ if(data.achievements.stream().anyMatch(a->a.id().equals(id))) player.achievements.add(id); }
    void recordPrologue(String id){ Models.Prologue e=data.prologueById.get(id); if(e!=null&&!player.prologue.contains(id)) player.prologue.add(id); }
    List<Models.Location> currentLocations(){ return data.locations.stream().filter(x->x.city().equals(player.city)).limit(60).toList(); }
    List<String> interactionCatalog(){ return data.interactions; }
    String calendarDate(){ int total=data.calendarDays.size(); if(total==0)return "Calendário indisponível"; int idx=Math.floorMod(player.day-1,total); String row=data.calendarDays.get(idx); String[] p=row.split("\\t",-1); return p.length>=6? p[1]+" "+p[2]+" — "+p[4]+(p[5].isBlank()?"":" • "+p[5]):"Dia "+player.day; }
    void searchForNpc(String npcId){Models.Npc n=data.npcById.get(npcId);if(n==null){message="Contato inexistente.";return;}if(player.relation(npcId)<10&&!player.flags.contains("friend_"+npcId)){message="Essa pessoa ainda não tem vínculo suficiente para justificar uma busca prolongada.";return;}int count=player.npcSearches.getOrDefault(npcId,0)+1;player.npcSearches.put(npcId,count);player.gold=Math.max(0,player.gold-5);player.day++;player.fatigue=Math.min(100,player.fatigue+1);if(count>=3){player.flags.add("npc_found_"+npcId);message="Depois de perguntar a comerciantes, viajantes e contatos da Guilda, você finalmente localizou "+n.name()+" em "+n.city()+". A pessoa também mudou com o tempo, como qualquer vida em Altheria.";}else message="Você reuniu uma nova pista sobre "+n.name()+". Tentativas de busca: "+count+"/3. A pista aponta para "+n.city()+".";}
}
