package mctsbot.gamestate;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import mctsbot.actions.Action;
import mctsbot.actions.BigBlindAction;
import mctsbot.actions.CallAction;
import mctsbot.actions.RaiseAction;
import mctsbot.actions.SmallBlindAction;

import com.biotools.meerkat.Card;
import com.biotools.meerkat.GameInfo;
import com.biotools.meerkat.Hand;
import com.biotools.meerkat.PlayerInfo;

public class GameState implements Cloneable {
	
	public static final int PREFLOP = 1;
	public static final int FLOP = 2;
	public static final int TURN = 3;
	public static final int RIVER = 4;
	
	private static final int MAX_ALLOWED_BET_MULTIPLE = 4;
	
	private static Card c1,c2;
	private static int botSeat;
	
	private double pot;
	private Hand table;
	
	//TODO: Chack that seat map is updated correctly
	private Map<Integer, Player> seatMap;
	private List<Player> activePlayers;
	
	private static int dealerSeat;
	
	//TODO make sure this will update correctly
	//TODO make private again
	public int nextPlayerToAct;
	
	private Action lastAction;
	
	private double betSize;
	private static double smallBlindSize;
	private static double bigBlindSize;
	
	private double maxBetThisRound;
	
	private int stage;
	
	private int committedPlayers;
	
	
	//TODO: remove unnecessary methods
	
	
	private GameState() { }
	
	
	public static GameState initialise(GameInfo gi) {
		final GameState newGameState = new GameState();
		
		GameState.smallBlindSize = gi.getSmallBlindSize();
		GameState.bigBlindSize = gi.getBigBlindSize();
		
		newGameState.pot = 0.0;
		newGameState.betSize = gi.getCurrentBetSize();
		newGameState.lastAction = null;
		newGameState.maxBetThisRound = 0;
		newGameState.stage = PREFLOP;
		newGameState.table = new Hand();
		newGameState.nextPlayerToAct = gi.getSmallBlindSeat();
		newGameState.activePlayers = new LinkedList<Player>();
		newGameState.committedPlayers = 0;
		
		
		int firstSeat = gi.nextActivePlayer(gi.getButtonSeat());
		int s = firstSeat;
		do {
			PlayerInfo pi = gi.getPlayer(s);
			newGameState.activePlayers.add(new Player(
					pi.getBankRoll(), 0.0, 0.0, new LinkedList<Action>(), s));
		} while((s=gi.nextActivePlayer(s))!=firstSeat);
		
		return newGameState;
	}
	
	public GameState holeCards(Card c1, Card c2, int seat) {
		GameState.c1 = c1;
		GameState.c2 = c2;
		GameState.botSeat = seat;
		
		return this;
	}
	
	
	public GameState doAction(int actionType) {
		
		final GameState newGameState = this.clone();
		
		// TODO: check input.
		
		final Player nextPlayer = getPlayer(nextPlayerToAct);
		System.out.print("nextPlayer is " + ((nextPlayer==null)?"":" not") + " null");
		System.out.print(", actionType = " + actionType);
		System.out.println(" and nextPlayerToAct = " + nextPlayerToAct);
		
		
		// Small Blind or Big Blind.
		if(actionType==Action.SMALL_BLIND || actionType==Action.BIG_BLIND) {
			if(stage!=PREFLOP) throw new RuntimeException(
					"doAction for a blind was called when not in PreFlop.");
			
			final RaiseAction blindAction = (actionType==Action.SMALL_BLIND)?
					new SmallBlindAction(smallBlindSize):new BigBlindAction(bigBlindSize);
					
			newGameState.activePlayers = new LinkedList<Player>(activePlayers);
			newGameState.activePlayers.remove(nextPlayer);
			newGameState.activePlayers.add(nextPlayer.doCallOrRaiseAction(blindAction));
			
		// Raise.
		} else if(actionType==Action.RAISE) {
			
			// Check to see if raising is allowed.
			// This isn't actually correct all the time, TODO: fix this
			if(MAX_ALLOWED_BET_MULTIPLE*betSize<maxBetThisRound+betSize) {
				System.out.println("max bet multiple reached, forcing call");
				return doAction(Action.CALL);
			}
			
			RaiseAction raiseAction = new RaiseAction(
					maxBetThisRound+betSize-nextPlayer.getAmountInPotInCurrentRound());
			
			newGameState.activePlayers = new LinkedList<Player>(activePlayers);
			newGameState.activePlayers.remove(nextPlayer);
			newGameState.activePlayers.add(nextPlayer.doCallOrRaiseAction(raiseAction));
			
			newGameState.committedPlayers = 1;
			
			
			newGameState.nextPlayerToAct = 
				(newGameState.isNextPlayerToAct()) ? 
						newGameState.getNextActivePlayerSeat(nextPlayerToAct) : -1;
			
			newGameState.pot += raiseAction.getAmount();
			newGameState.maxBetThisRound += betSize;
			
		// Call.
		} else if(actionType==Action.CALL) {
			
			final CallAction callAction = 
				new CallAction(maxBetThisRound-nextPlayer.getAmountInPotInCurrentRound());
			
			newGameState.activePlayers = new LinkedList<Player>(activePlayers);
			newGameState.activePlayers.remove(nextPlayer);
			newGameState.activePlayers.add(nextPlayer.doCallOrRaiseAction(callAction));
			
			newGameState.committedPlayers++;
			
			newGameState.nextPlayerToAct = 
				(newGameState.isNextPlayerToAct()) ? 
						newGameState.getNextActivePlayerSeat(nextPlayerToAct) : -1;
			
			newGameState.pot += callAction.getAmount();
			
			
		// Fold.
		} else if(actionType==Action.FOLD) {
			
			newGameState.activePlayers = new LinkedList<Player>(activePlayers);
			newGameState.activePlayers.remove(nextPlayer);
			// TODO: Add folded player to inactive player list?
			
			newGameState.nextPlayerToAct = 
				(newGameState.isNextPlayerToAct()) ? 
						newGameState.getNextActivePlayerSeat(nextPlayerToAct) : -1;
			
			
		// Invalid actionType?
		} else {
			throw new RuntimeException("Invalid actionType passed to doAction: " + actionType);
		}
		
		return newGameState;
		
	}
	
	public boolean roundOver() {
		return activePlayers.size()==committedPlayers;
	}
	
	public GameState dealCard(Card card) {
		final GameState newGameState = this.clone();
		
		newGameState.table = new Hand(table);
		newGameState.table.addCard(card);
		
		return newGameState;
	}
	
	public Action getLastAction() {
		return lastAction;
	}
	
	public boolean isNextPlayerToAct() {
		//return nextPlayerToAct!=-1;
		System.out.println("committedPlayers = " + committedPlayers + " activePlayers = " + activePlayers.size());
		if(activePlayers.size()<=1) return false;
		return (committedPlayers<activePlayers.size());
	}
	
	public boolean isBotNextPlayerToAct() {
		return botSeat==nextPlayerToAct;
	}
	
	
	//TODO: make this more efficient.
	public int getNextActivePlayerSeat(int seat) {
		if(activePlayers.size()==1) return activePlayers.get(0).getSeat();
		if(activePlayers.size()<=0) return -1;
		
		int nextSeat = seat;
		for(Player p: activePlayers) {
			final int pSeat = p.getSeat();
			if(nextSeat>seat) {
				if(pSeat>seat && pSeat<nextSeat) {
					nextSeat = pSeat;
				}
			} else
				if(pSeat<=nextSeat || pSeat>seat) {
					nextSeat = pSeat;
				} 
			}
		return nextSeat;
	}
	
	
	//TODO: make this more efficient.
	public Player getNextActivePlayer(int seat) {
		if(activePlayers.size()==1) return getPlayer(seat);
		if(activePlayers.size()<=0) return null;
		
		Player nextPlayer = null;
		int nextSeat = seat;
		for(Player p: activePlayers) {
			final int pSeat = p.getSeat();
			if(nextSeat>seat) {
				if(pSeat>seat && pSeat<nextSeat) {
					nextSeat = pSeat;
					nextPlayer = p;
				}
			} else
				if(pSeat<=nextSeat || pSeat>seat) {
					nextSeat = pSeat;
					nextPlayer = p;
				} 
			}
		return nextPlayer;
	}
		
	
	
	public Player getPlayer(int seat) {
		/*for(Player p:activePlayers) {
			if(p.getSeat()==seat) return p;
		}
		return null;*/
		return seatMap.get(seat);
	}
	
	public double getBetSize() {
		return betSize;
	}
	
	public double getAmountToCall(int seat) {
		return maxBetThisRound-getPlayer(seat).getAmountInPotInCurrentRound();
	}
	
	public int getStage() {
		return stage;
	}

	public int getBotSeat() {
		return botSeat;
	}
	
	public int getDealerSeat() {
		return dealerSeat;
	}
	
	public int getNoOfActivePlayers() {
		return activePlayers.size();
	}
	
	public Card getC1() {
		return c1;
	}
	
	public Card getC2() {
		return c2;
	}
	
	public Hand getTable() {
		return table;
	}
	
	public GameState goToNextStage() {
		final GameState newGameState = this.clone();
		
		if(stage==FLOP) newGameState.betSize = betSize*2;
		
		if(!(stage==PREFLOP || stage==FLOP || stage==TURN)) throw new RuntimeException(
				"goToNextStage called while not in PreFlop, Flop or Turn.");
		
		newGameState.stage = stage+1;
		
		newGameState.activePlayers = new LinkedList<Player>();
		for(Player p: activePlayers) {
			newGameState.activePlayers.add(p.newRound());
		}
		
		newGameState.nextPlayerToAct = getNextActivePlayerSeat(dealerSeat);
		
		newGameState.maxBetThisRound = 0.0;
		
		newGameState.committedPlayers = 0;
		
		return newGameState;
	}
	
	public GameState clone() {
		final GameState newGameState = new GameState();
		
		newGameState.pot = pot;
		newGameState.table = table;
		newGameState.activePlayers = activePlayers;
		newGameState.nextPlayerToAct = nextPlayerToAct;
		newGameState.lastAction = lastAction;
		newGameState.betSize = betSize;
		newGameState.maxBetThisRound = maxBetThisRound;
		newGameState.stage = stage;
		newGameState.committedPlayers = committedPlayers;
		newGameState.seatMap = seatMap;
		
		return newGameState;
	}

	
}



