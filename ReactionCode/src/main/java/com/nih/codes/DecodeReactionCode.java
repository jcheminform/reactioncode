package com.nih.codes;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nih.reaction.additionalConstants.BOND_CHANGE_INFORMATION;
import static com.nih.reaction.additionalConstants.BOND_CLEAVED;
import static com.nih.reaction.additionalConstants.BOND_FORMED;
import static com.nih.reaction.additionalConstants.IS_STEREOCENTER;
import static com.nih.reaction.additionalConstants.BOND_ORDER;
import static com.nih.reaction.additionalConstants.BOND_ORDER_CHANGE;
import static com.nih.reaction.additionalConstants.BOND_ORDER_GAIN;
import static com.nih.reaction.additionalConstants.BOND_ORDER_REDUCED;
import static com.nih.reaction.additionalConstants.REACTION_CENTER;
import static com.nih.reaction.additionalConstants.BOND_STEREO;
import static com.nih.reaction.additionalConstants.STEREO_TYPE;

import org.openscience.cdk.Atom;
import org.openscience.cdk.AtomContainer;
import org.openscience.cdk.Bond;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.Reaction;
import org.openscience.cdk.aromaticity.Kekulization;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IDoubleBondStereochemistry;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.interfaces.IReactionSet;
import org.openscience.cdk.interfaces.IStereoElement;
import org.openscience.cdk.interfaces.ITetrahedralChirality;
import org.openscience.cdk.layout.StructureDiagramGenerator2;
import org.openscience.cdk.stereo.Atropisomeric;
import org.openscience.cdk.stereo.DoubleBondStereochemistry;
import org.openscience.cdk.stereo.ExtendedCisTrans;
import org.openscience.cdk.stereo.ExtendedTetrahedral;
import org.openscience.cdk.stereo.Octahedral;
import org.openscience.cdk.stereo.SquarePlanar;
import org.openscience.cdk.stereo.TetrahedralChirality;
import org.openscience.cdk.stereo.TrigonalBipyramidal;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.periodictable.PeriodicTable;

import com.google.common.base.Splitter;
import com.nih.fragments.FragmentUtils;
import com.nih.tools.ElementCalculation;
import com.nih.tools.tools;

public class DecodeReactionCode {
	
	int idReaction;
	String cReactionCode;

	private HashMap<String,Integer> reverseConnectionTableAlphabet = new HashMap<String,Integer>();
	private Set<IAtom> reactionCenter = new HashSet<IAtom>();
	private Set<IBond> bondsCleaved = new HashSet<IBond>();
	private Set<IBond> bondsFormed = new HashSet<IBond>();
	private Set<IBond> bondsOrder = new HashSet<IBond>();
	
	private List<String> errors = new ArrayList<String>();
	
	/**
	 * 
	 */
	public DecodeReactionCode() {
//		reverseConnectionTableAlphabet = makereverseConnectionTableAlphabet();
//		periodicTable = new PeriodicTable();
	}
	
	/**
	 * 
	 */
	private void init() {
		reverseConnectionTableAlphabet = makereverseConnectionTableAlphabet();
		reactionCenter = new HashSet<IAtom>();
		bondsCleaved = new HashSet<IBond>();
		bondsFormed = new HashSet<IBond>();
		bondsOrder = new HashSet<IBond>();
		errors = new ArrayList<String>();
	}
	
	/**
	 * @param reactionCodes
	 * @return
	 * @throws CDKException
	 * @throws CloneNotSupportedException
	 */
	public IReactionSet makeReactions(List<String> reactionCodes) throws CDKException, CloneNotSupportedException {
		init();

		IReactionSet reactions = DefaultChemObjectBuilder.getInstance().newInstance(IReactionSet.class);
		idReaction = 0;
		for (String reactionCode : reactionCodes) {
			IReaction reaction;
			cReactionCode = reactionCode;
			try {
				reaction = decode(reactionCode);
				reaction.setID(idReaction+"");
			}
			catch (Exception e){
				reaction = new Reaction();
				reaction.setID(idReaction+"");
				errors.add(idReaction + "\t decoding failure \t" + reactionCode + "\n");
			} 
			
			if (reaction.getProperty("mappingError") != null) {
				errors.add(idReaction + "\t mapping error \t" + reactionCode + "\n");
			}
			reactions.addReaction(reaction);
			idReaction++;
		}
		reactions.setProperty("errors", errors);
		return reactions; 
	}
	
	public IReaction decode(String code) throws CDKException, CloneNotSupportedException {
		init();
		IAtomContainer pseudoMolecule = makePseudoMolecule(code);
		IReaction reaction = makeReaction(pseudoMolecule);
		reaction.setProperty("reactionCenter", reactionCenter);
		reaction.setProperty("bondsCleaved", bondsCleaved);
		reaction.setProperty("bondsFormed", bondsFormed);
		reaction.setProperty("bondsOrder", bondsOrder);
		//generate coordinates
		try {
			cleanReaction(reaction);
		} catch (CDKException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	return reaction; 
	}
	
	/**
	 * generate coordinates
	 * @param mol
	 * @return
	 * @throws CDKException
	 */
	private void cleanReaction(IReaction reaction) throws CDKException {
		//use StructureDiagramGenerator2 because StructureDiagramGenerator modify the stereo and can remove it
		StructureDiagramGenerator2 sdg = new StructureDiagramGenerator2();
		for (IAtomContainer mol : reaction.getAgents().atomContainers()) {
			sdg.setMolecule(mol, false);
			sdg.generateCoordinates();
		}
		for (IAtomContainer mol : reaction.getReactants().atomContainers()) {
			sdg.setMolecule(mol, false);
			sdg.generateCoordinates();
		}
		for (IAtomContainer mol : reaction.getProducts().atomContainers()) {
			sdg.setMolecule(mol, false);
			sdg.generateCoordinates();
		}
	}
	
	/**
	 * @param code
	 * @return
	 */
	private IAtomContainer makePseudoMolecule(String code) {
		
		IAtomContainer pseudoMolecule = new AtomContainer();
		Map<Integer,IAtom> atomIndex = new HashMap<Integer,IAtom>();
		
		String[] layers = code.split("\\|");
		
		//detect version
		int shift = detectVersion(layers[0]);
//		System.out.println("shift " + shift);
		
		if (shift == -1) {
			System.err.println("Unknown format");
			return null;
		}
		int leavingFirstIndex = -1;
		
		for (String layer : layers) {
			boolean staying = Character.isDigit(layer.charAt(0)) ? true : false;
			
			if (layer.charAt(0) == 'A' && leavingFirstIndex == -1) {
				leavingFirstIndex = atomIndex.size();
			}
			
			layer = layer.substring(layer.indexOf(":")+1);
			String[] subLayers = layer.split("/");
			String[] atomsBondsAndRepetition = subLayers[0].split("]");
			
//			System.out.println("layer " + layer);
//			System.out.println("subLayers " + Arrays.toString(subLayers));
//			System.out.println("atomsBondsAndRepetition " + Arrays.toString(atomsBondsAndRepetition));
			
			Map<Integer,String> charges = new HashMap<Integer,String>();
			Map<Integer,String> stereoInfo = new HashMap<Integer,String>();
			Map<Integer,String> isotopes = new HashMap<Integer,String>();
			
			//get specific properties of atoms and bonds 
			for (String subLayer : subLayers) {
				if (subLayer.charAt(0) == 'c') {
					subLayer = subLayer.substring(1);
					//String[] properties = subLayer.split(";");
					Iterable<String> properties = Splitter.fixedLength(4).split(subLayer);
					for (String property : properties) {
						int index = Integer.parseInt(property.substring(0, 2));
						String charge =  property.substring(2);
						charges.put(index, charge);
					}
				}
				if (subLayer.charAt(0) == 's') {
					subLayer = subLayer.substring(1);
					//String[] properties = subLayer.split(";");
					Iterable<String> properties = Splitter.fixedLength(4).split(subLayer);
					for (String property : properties) {
						int index = Integer.parseInt(property.substring(0, 2));
						String stereo =  property.substring(2);
						stereoInfo.put(index, stereo);
					}
				}
				if (subLayer.charAt(0) == 'i') {
					subLayer = subLayer.substring(1);
					//String[] properties = subLayer.split(";");
					Iterable<String> properties = Splitter.fixedLength(4).split(subLayer);
					for (String property : properties) {
						int index = Integer.parseInt(property.substring(0, 2));
						String isotope =  property.substring(2);
						isotopes.put(index, isotope);
					}
				}
			}
			
//			System.out.println("charges " + charges);
//			System.out.println("stereoInfo " + stereoInfo);
//			System.out.println("isotopes " + isotopes);
			
			int cpt = 0;
			for (String atomAndBondsAndRepetition : atomsBondsAndRepetition) {
				String[] splitAtomAndBonds = atomAndBondsAndRepetition.split("\\(");
				String[] splitBondsAndRepetions = splitAtomAndBonds[1].split("\\)");
				String atomCode = splitAtomAndBonds[0];
				String bondsCode = splitBondsAndRepetions[0];
				double repetition = Double.parseDouble(splitBondsAndRepetions[1].replace("[", "").replace("]", ""));
				
//				System.out.println("atomCode " + atomCode);
//				System.out.println("bondsCodes " + bondsCode);
//				System.out.println("repetition " + repetition);
				
				IAtom atom = makeAtom(atomCode, shift);
				int mapping = pseudoMolecule.getAtomCount();
				atom.setID(mapping+"");
				atom.setProperty(CDKConstants.ATOM_ATOM_MAPPING, mapping+1);
				
				if (charges.containsKey(cpt)) {
					String charge = charges.get(cpt);
					int inPro = decodeChargeAndIsotope(charge.charAt(1)+"");
					atom.setFormalCharge(inPro);
					atom.setProperty("chargeInReactant", decodeChargeAndIsotope(charge.charAt(0)+""));
					atom.setProperty("chargeInProduct", inPro);
				}
				if (stereoInfo.containsKey(cpt)) {
					String stereo = stereoInfo.get(cpt);
					atom.setProperty(STEREO_TYPE, stereo);
					atom.setProperty(IS_STEREOCENTER, true);
					atom.setProperty("configurationInReactant", decodeAtomStereo(atom, stereo.charAt(0)+""));
					atom.setProperty("configurationInProduct", decodeAtomStereo(atom, stereo.charAt(1)+""));
				}
				if (isotopes.containsKey(cpt)) {
					String isotope = isotopes.get(cpt);
					int inPro = decodeChargeAndIsotope(isotope.charAt(1)+"");
					atom.setMassNumber(ElementCalculation.calculateMass(atom.getSymbol()) + inPro);
					atom.setProperty("isotopeInReactant", decodeChargeAndIsotope(isotope.charAt(0)+""));
					atom.setProperty("isotopeInProduct", inPro);
				}
				//Determine reaction centre status
				if (Integer.parseInt(atomCode.substring(0, 1)) > 0) {
					atom.setProperty(REACTION_CENTER, true);
				}
				else {
					atom.setProperty(REACTION_CENTER, false);
				}
				atom.setProperty("repetition", repetition);
				atomIndex.put(pseudoMolecule.getAtomCount(), atom);
//				System.out.println(atom);
//				System.out.println(atom.getProperties());
				pseudoMolecule.addAtom(atom);
				cpt++;
				
				for (int i = 0; i < bondsCode.length(); i += 4) {
					String orders = bondsCode.substring(i, i+2);
					String position = bondsCode.substring(i+2, i+4);
//					System.out.println("orders " + orders + " position " + position);
					IBond bond = new Bond();
					if (staying) {
						if (position.equals("00")) {
							pseudoMolecule.setProperty("mappingError", true);
							continue;
						}
						bond.setAtom(atomIndex.get(reverseConnectionTableAlphabet.get(position)), 0);
					}
					else {
						// si G ou apres connection avec staying autrement living TESTER
						if (position.compareTo("GG") < 0) {
							int newPosition = leavingFirstIndex + Integer.parseInt(position,16); 
							bond.setAtom(atomIndex.get(newPosition), 0);
						}
						//connect with a staying atom
						else {
							bond.setAtom(atomIndex.get(reverseConnectionTableAlphabet.get(position)), 0);
						}
					}
					bond.setAtom(atom, 1);
					bond.setID(pseudoMolecule.getBondCount()+"");
					
					if (stereoInfo.containsKey(cpt)) {
						String stereoInBothReactantAndProduct = stereoInfo.get(cpt);
						IBond.Stereo stereoInReactant = decodeBondStereo(Integer.parseInt(
								stereoInBothReactantAndProduct.charAt(0)+""));
						IBond.Stereo stereoInProduct = decodeBondStereo(Integer.parseInt(
								stereoInBothReactantAndProduct.charAt(1)+""));
						if (stereoInProduct != null || stereoInReactant != null) {
							bond.setStereo(stereoInProduct);
							bond.setProperty(BOND_STEREO, true);
							bond.setProperty("stereoInReactant", stereoInReactant);
							bond.setProperty("stereoInProduct", stereoInProduct);
						}
					}
					int reactantOrder = Integer.parseInt(orders.substring(0,1));
					int productOrder = Integer.parseInt(orders.substring(1));
					if (productOrder == 0) {
						bond.setOrder(decodeBondOrder(reactantOrder));
					}
					else {
						bond.setOrder(decodeBondOrder(productOrder));
					}
					if (productOrder == 9) {
						bond.setIsAromatic(true);
						bond.getBegin().setIsAromatic(true);
						bond.getEnd().setIsAromatic(true);
					}
					else {
						bond.setIsAromatic(false);
					}
					bond.setProperty("orderInReactant", reactantOrder);
					//Determine bond status (cleaved, formed, order change)
					determineBondStatus(bond , reactantOrder, productOrder);
					//add repetition property
					double bondRepetition = ((double) bond.getBegin().getProperty("repetition") > (double) bond.getEnd().getProperty("repetition"))
							? bond.getBegin().getProperty("repetition") : bond.getEnd().getProperty("repetition");
					bond.setProperty("repetition", bondRepetition);
//					System.out.println(bond);
//					System.out.println(bond.getProperties());
					pseudoMolecule.addBond(bond);
					cpt++;
				}
			}
		}
		
		return pseudoMolecule;
	}


	/**
	 * @param ac
	 * @param bond
	 * @param repeatedAtoms
	 * @param repetition
	 */
	private void addRepeatedBond(IAtomContainer ac, IBond bond, Map<IAtom,List<IAtom>> repeatedAtoms, int repetition) {
		List <IAtom> rbegins = repeatedAtoms.get(bond.getBegin());
		List <IAtom> rends = repeatedAtoms.get(bond.getEnd());
		for (int i = 0; i < repetition; i++) {
			IBond repeated = shallowCopyBond(bond);
			IAtom rbegin = repeated.getAtom(0);
			IAtom rend = repeated.getAtom(1);
			if (rbegins.size() > 1){
				rbegin = rbegins.get(i);
				repeated.setAtom(rbegin, 0);
			}
			if (rends.size() > 1) {
				rend = rends.get(i);
				repeated.setAtom(rend, 1);
			}
			if (ac.getBond(repeated.getBegin(), repeated.getEnd()) != null)
				continue;
			ac.addBond(repeated);
		}
	}
	
	/**
	 * @param pseudoMolecule
	 * @return
	 * @throws CDKException
	 * @throws CloneNotSupportedException
	 */
	private IReaction makeReaction(IAtomContainer pseudoMolecule) throws CDKException, CloneNotSupportedException {
		IReaction reaction = new Reaction();
		
		IAtomContainer aggregateReactants = new AtomContainer();
		IAtomContainer aggregateProducts = new AtomContainer();
		
		Set<IAtom> stereocenterInReactant = new HashSet<IAtom>();
		Set<IAtom> stereocenterInProduct = new HashSet<IAtom>();
		
		Map<IAtom,List<IAtom>> repeatedAtoms = new HashMap<IAtom,List<IAtom>>();
		
		for (IAtom atom : pseudoMolecule.atoms()) {
			int repetition = (int)((double)atom.getProperty("repetition"));
			aggregateReactants.addAtom(atom);
			aggregateProducts.addAtom(atom);
			//if (repetition > 1) {
				List<IAtom> atoms = new ArrayList<IAtom>();
				atoms.add(atom);
				for (int i = 1; i < repetition; i++) {
					IAtom repeated = new Atom(atom);
					repeated.setProperties(atom.getProperties());
					aggregateProducts.addAtom(repeated);
					atoms.add(repeated);
				}
				repeatedAtoms.put(atom, atoms);
			//}
			//else {
			//	repeatedAtoms.put(atom, new ArrayList<IAtom>());
			//}
		}
		List<IBond> madeBondtoAdd = new ArrayList<IBond>();
		List<IAtom> atomsInvolvedInMadeBondtoAdd = new ArrayList<IAtom>();
		for (IBond bond : pseudoMolecule.bonds()) {
			int repetition = (int)((double)bond.getProperty("repetition"));
			if (bond.getProperty(BOND_CHANGE_INFORMATION) != null) {
				if ((int)bond.getProperty(BOND_CHANGE_INFORMATION) == BOND_CLEAVED) {
					aggregateReactants.addBond(bond);
					bondsCleaved.add(bond);
					reactionCenter.add(bond.getBegin());
					reactionCenter.add(bond.getEnd());
				}
				else if ((int)bond.getProperty(BOND_CHANGE_INFORMATION) == BOND_FORMED) {
					//aggregateProducts.addBond(bond);
					if (repetition > 1) {
						//addRepeatedBond(aggregateProducts, bond, repeatedAtoms, repetition, null, madeBondCheck);
						madeBondtoAdd.add(bond);
						IAtom begin = bond.getBegin();
						IAtom end = bond.getEnd();
						if (!atomsInvolvedInMadeBondtoAdd.contains(begin)) {
							begin.setProperty("nRep", repeatedAtoms.get(begin).size());
							atomsInvolvedInMadeBondtoAdd.add(begin);
						}
						if (!atomsInvolvedInMadeBondtoAdd.contains(end)){
							end.setProperty("nRep", repeatedAtoms.get(end).size());
							atomsInvolvedInMadeBondtoAdd.add(end);
						}
					}
					else {
						aggregateProducts.addBond(bond);
					}
				}
				else if ((int)bond.getProperty(BOND_CHANGE_INFORMATION) == BOND_ORDER) {
					IBond copy = shallowCopyBond(bond);
					int order = copy.getProperty("orderInReactant");
					copy.setOrder(decodeBondOrder(order));
					if (order == 9) {
						copy.setIsAromatic(true);
						copy.getBegin().setIsAromatic(true);
						copy.getEnd().setIsAromatic(true);
					}
					else {
						copy.setIsAromatic(false);
					}
					aggregateReactants.addBond(copy);
					//aggregateProducts.addBond(bond);
					if (repetition > 1) {
						//do not add in cleaved. It's made later
						addRepeatedBond(aggregateProducts, bond, repeatedAtoms, repetition);
					}
					else {
						aggregateProducts.addBond(bond);
					}
					bondsOrder.add(copy);
					reactionCenter.add(bond.getBegin());
					reactionCenter.add(bond.getEnd());
				}
			}
			else {
				IBond copy = shallowCopyBond(bond);
				IBond.Stereo stereoInReactant = bond.getProperty("stereoInReactant");
				if (stereoInReactant != null) {
					copy.setStereo(bond.getProperty("stereoInReactant"));
				}
				aggregateReactants.addBond(copy);
				aggregateProducts.addBond(bond);
				if (repetition > 1) {
					//do not add in cleaved. It's made later
					addRepeatedBond(aggregateProducts, bond, repeatedAtoms, repetition);
				}
			}
			if (bond.getProperty(BOND_STEREO) != null) {
				if (bond.getBegin().getProperty(IS_STEREOCENTER) != null) {
					stereocenterInProduct.add(bond.getBegin());
					stereocenterInReactant.add(bond.getBegin());	
				}
				if (bond.getEnd().getProperty(IS_STEREOCENTER) != null) {
					stereocenterInProduct.add(bond.getEnd());
					stereocenterInReactant.add(bond.getEnd());
				}
			}
		}
		
		//add made bonds, which involve repeated atoms
		Collections.sort(atomsInvolvedInMadeBondtoAdd, new CompareByRep());
		for (IAtom a : atomsInvolvedInMadeBondtoAdd) {
			List<IAtom> atoms = repeatedAtoms.get(a);
			for (IAtom r : atoms) {
				for (IBond b : madeBondtoAdd) {
					if (b.getAtom(0).equals(a)) {
						b.setAtom(r, 0);
						break;
					}
					if (b.getAtom(1).equals(a)) {
						b.setAtom(r, 1);
						break;
					}
				}
			}
		}

		for (IBond b : madeBondtoAdd) {
			aggregateProducts.addBond(b);
			System.out.println(b);
		}

		aggregateProducts = aggregateProducts.clone();
		for (IBond bond : aggregateProducts.bonds()) {
			if (bond.getProperty(BOND_CHANGE_INFORMATION) != null) {
				if ((int)bond.getProperty(BOND_CHANGE_INFORMATION) == BOND_FORMED) {
					bondsFormed.add(bond);
					reactionCenter.add(bond.getBegin());
					reactionCenter.add(bond.getEnd());
				}
				else if ((int)bond.getProperty(BOND_CHANGE_INFORMATION) == BOND_ORDER) {
					bondsOrder.add(bond);
					reactionCenter.add(bond.getBegin());
					reactionCenter.add(bond.getEnd());
				}
			}
		}
		
		for (IAtom a : aggregateReactants.atoms()) {
			if (a.getProperty("chargeInReactant") != null) {
				a.setFormalCharge((int)a.getProperty("chargeInReactant"));
			}
			if (a.getProperty("isotopeInReactant") != null) {
				a.setMassNumber(ElementCalculation.calculateMass(a.getSymbol()) + (int)a.getProperty("isotopeInReactant"));
			}
		}
		
		//make index
		Map<String, IAtom> index = new HashMap<String, IAtom>();
		for (IAtom atom : aggregateProducts.atoms()) {
			index.put(atom.getID(), atom);
		}
		
		//replace atoms of stereocenterInProduct by their deep copy
		Set<IAtom> newStereocenterInProduct = new HashSet<IAtom>();
		for (IAtom atom : stereocenterInProduct) {
			newStereocenterInProduct.add(index.get(atom.getID()));
		}
		
		//configure stereo
		try {
			if (!stereocenterInReactant.isEmpty()) {
				addStereo(aggregateReactants, stereocenterInReactant, "configurationInReactant");
			}

			if (!stereocenterInProduct.isEmpty()) {
				addStereo(aggregateProducts, newStereocenterInProduct, "configurationInProduct");
			}
		}
		catch (Exception e) {
			
		}
		
		//kekulize
		try {
			Kekulization.kekulize(aggregateReactants);
			Kekulization.kekulize(aggregateProducts);
		}
		catch (Exception e) {
			errors.add(idReaction + "\t can't be kekulized \t" + cReactionCode + "\n");
		}
		
		//configureAtomAndType
		AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(aggregateReactants);
		tools.addMissingHydrogen(aggregateReactants);
		
		AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(aggregateProducts);
		tools.addMissingHydrogen(aggregateProducts);
				
		IAtomContainerSet reactants = FragmentUtils.makeAtomContainerSet(aggregateReactants);
		
		FragmentUtils.resetVisitedFlags(aggregateProducts);
		IAtomContainerSet products = FragmentUtils.makeAtomContainerSet(aggregateProducts);
		
		reaction.setReactants(reactants);
		for (IAtomContainer ac : reactants.atomContainers()) {
			reaction.setReactantCoefficient(ac, ac.getAtom(0).getProperty("repetition"));
		}
		reaction.setProducts(products);
		for (IAtomContainer ac : products.atomContainers()) {
			reaction.setReactantCoefficient(ac, 1.0);
		}
		
		if (pseudoMolecule.getProperty("mappingError") != null) {
			reaction.setProperty("mappingError", true);
		}

		return reaction;
	}
	
	
	/**
	 * @param code
	 * @return
	 */
	private int detectVersion(String code) {
		String firstAtom = code.split("\\:")[1].split("\\(")[0];
		if (firstAtom.length() == 3)
			return 1;
		else if (firstAtom.length() == 5)
			return 3;
		else 
			return -1;
	}
	
	/**
	 * @param code
	 * @param shift
	 * @return
	 */
	private IAtom makeAtom(String code, int shift) {
		IAtom atom = new Atom();
		atom.setProperty("status", Integer.parseInt(String.valueOf(code.charAt(0))));
		
		int nextChar = 1;
		if (shift == 3) {
			atom.setProperty("rHybridization", String.valueOf(code.charAt(1)));
			atom.setProperty("pHybridization", String.valueOf(code.charAt(2)));
			nextChar = 3;
		}
		
		//get symbol 
		configureAtom(atom, code.substring(nextChar));
		atom.setImplicitHydrogenCount(0);
		
		return atom;
	}
	
	/**
	 * @param atom
	 * @param code
	 */
	private void configureAtom(IAtom atom, String code) {
		String symbol;
		int mass = -1;
		if (code.equals("FF")) {
			symbol = "R";
        } 
		else if (code.equals("FE")) {
			symbol = "*";
        } 
		else if (code.equals("FD")) {
			symbol = "D";
			mass = 2;
        } 
		else if (code.equals("FV")) {
			symbol = "F";
			mass = 2;
        } 
        else {
        	//convert atomic number form hex to decimal and get symbol
        	symbol = PeriodicTable.getSymbol(Integer.parseInt(code,16));
        }
		atom.setSymbol(symbol);
		if (mass < 0)
            atom.setMassNumber(null);
        else
            atom.setMassNumber(mass);
	}
	
	/**
	 * @param atom
	 * @param stereo
	 * @return
	 */
	private String decodeAtomStereo(IAtom atom, String stereo) {
		if (stereo.equals("1")) {
			return "@TH1";
		}
		else if (stereo.equals("2")) {
			return "@TH2";
		}
		else if (stereo.equals("3")) {
			return "@AL1";
		}
		else if (stereo.equals("4")) {
			return "@AL2";
		}
		else if (stereo.equals("5")) {
			return "@DB1";
		}
		else if (stereo.equals("6")) {
			return "@DB2";
		}
		else if (stereo.equals("7")) {
			return "@CT1";
		}
		else if (stereo.equals("8")) {
			return "@CT2";
		}
		else if (stereo.equals("9")) {
			return "@SP1";
		}
		else if (stereo.equals("A")) {
			return "@SP2";
		}
		else if (stereo.equals("B")) {
			return "@SP3";
		}
		else if (stereo.equals("C")) {
			return "@TB1";
		}
		else if (stereo.equals("D")) {
			return "@TB2";
		}
		else if (stereo.equals("E")) {
			return "@OH1";
		}
		else if (stereo.equals("F")) {
			return "@OH2";
		}
		else if (stereo.equals("G")) {
			return "@AP1";
		}
		else if (stereo.equals("H")) {
			return "@AP2";
		}
		else {
			return null;
		}
	}
	
	/**
	 * @param chargeOrMass
	 * @return
	 */
	private int decodeChargeAndIsotope(String chargeOrMass) {
		int chargeDecoded = 0;
		if (chargeOrMass.equals("1")) {
			chargeDecoded = -17;
		}
		else if (chargeOrMass.equals("2")) {
			chargeDecoded = -16;
		}
		else if (chargeOrMass.equals("3")) {
			chargeDecoded = -15;
		}
		else if (chargeOrMass.equals("4")) {
			chargeDecoded = -14;
		}
		else if (chargeOrMass.equals("5")) {
			chargeDecoded = -13;
		}
		else if (chargeOrMass.equals("6")) {
			chargeDecoded = -12;
		}
		else if (chargeOrMass.equals("7")) {
			chargeDecoded = -11;
		}
		else if (chargeOrMass.equals("8")) {
			chargeDecoded = -10;
		}
		else if (chargeOrMass.equals("9")) {
			chargeDecoded = -9;
		}
		else if (chargeOrMass.equals("A")) {
			chargeDecoded = -8;
		}
		else if (chargeOrMass.equals("B")) {
			chargeDecoded = -7;
		}
		else if (chargeOrMass.equals("C")) {
			chargeDecoded = -6;
		}
		else if (chargeOrMass.equals("D")) {
			chargeDecoded = -5;
		}
		else if (chargeOrMass.equals("E")) {
			chargeDecoded = -4;
		}
		else if (chargeOrMass.equals("F")) {
			chargeDecoded = -3;
		}
		else if (chargeOrMass.equals("G")) {
			chargeDecoded = -2;
		}
		else if (chargeOrMass.equals("H")) {
			chargeDecoded = -1;
		}
		else if (chargeOrMass.equals("I")) {
			chargeDecoded = 1;
		}
		else if (chargeOrMass.equals("J")) {
			chargeDecoded = 2;
		}
		else if (chargeOrMass.equals("K")) {
			chargeDecoded = 3;
		}
		else if (chargeOrMass.equals("L")) {
			chargeDecoded = 4;
		}
		else if (chargeOrMass.equals("M")) {
			chargeDecoded = 5;
		}
		else if (chargeOrMass.equals("N")) {
			chargeDecoded = 6;
		}
		else if (chargeOrMass.equals("O")) {
			chargeDecoded = 7;
		}
		else if (chargeOrMass.equals("P")) {
			chargeDecoded = 8;
		}
		else if (chargeOrMass.equals("Q")) {
			chargeDecoded = 9;
		}
		else if (chargeOrMass.equals("R")) {
			chargeDecoded = 10;
		}
		else if (chargeOrMass.equals("S")) {
			chargeDecoded = 11;
		}
		else if (chargeOrMass.equals("T")) {
			chargeDecoded = 12;
		}
		else if (chargeOrMass.equals("U")) {
			chargeDecoded = 13;
		}
		else if (chargeOrMass.equals("V")) {
			chargeDecoded = 14;
		}
		else if (chargeOrMass.equals("W")) {
			chargeDecoded = 15;
		}
		else if (chargeOrMass.equals("X")) {
			chargeDecoded = 16;
		}
		else if (chargeOrMass.equals("Y")) {
			chargeDecoded = 17;
		}

		return chargeDecoded;
	}
	
	/**
	 * @param order
	 * @return
	 */
	private IBond.Order decodeBondOrder(int order) {
		if (order == 1)
			return IBond.Order.SINGLE;
		else if (order == 2)
			return IBond.Order.DOUBLE;
		else if (order == 3)
			return IBond.Order.TRIPLE;
		else if (order == 4)
			return IBond.Order.QUADRUPLE;
		else if (order == 5)
			return IBond.Order.QUINTUPLE;
		else if (order == 6)
			return IBond.Order.SEXTUPLE;
		else if (order == 9)
			return IBond.Order.SINGLE;
		else 
			return IBond.Order.UNSET;
	}
	
	/**
	 * @param stereo
	 * @return
	 */
	private IBond.Stereo decodeBondStereo(int stereo) {
		if (stereo == 1)
			return IBond.Stereo.E;
		else if (stereo == 2)
			return IBond.Stereo.Z;
		else if (stereo == 3)
			return IBond.Stereo.DOWN_INVERTED;
		else if (stereo == 4)
			return IBond.Stereo.DOWN;
		else if (stereo == 5)
			return IBond.Stereo.UP_INVERTED;
		else if (stereo == 6)
			return IBond.Stereo.UP;
		else if (stereo == 7)
			return IBond.Stereo.E_OR_Z;
		else if (stereo == 8)
			return IBond.Stereo.UP_OR_DOWN;
		else if (stereo == 9)
			return IBond.Stereo.UP_OR_DOWN_INVERTED;
		else
			return null;
	}

	/**
	 * determine status by looking order difference 1->2 order change 0->1 formed and 1->0 cleaved
	 * @param bond
	 * @param inReactant
	 * @param inProduct
	 */
	private void determineBondStatus(IBond bond , int inReactant, int inProduct) {
		if (inReactant == 0 && inProduct > 0)
			bond.setProperty(BOND_CHANGE_INFORMATION, BOND_FORMED);
		else if (inReactant > 0 && inProduct == 0)
			bond.setProperty(BOND_CHANGE_INFORMATION, BOND_CLEAVED);
		else if (inReactant > 0 && inProduct > 0 && inReactant < inProduct) {
			bond.setProperty(BOND_CHANGE_INFORMATION, BOND_ORDER);
			bond.setProperty(BOND_ORDER_CHANGE, BOND_ORDER_GAIN);
		}
		else if (inReactant > 0 && inProduct > 0 && inReactant > inProduct) {
			bond.setProperty(BOND_CHANGE_INFORMATION, BOND_ORDER);
			bond.setProperty(BOND_ORDER_CHANGE, BOND_ORDER_REDUCED);
		}
		else {
			bond.setProperty(BOND_CHANGE_INFORMATION, null);
		}
	}
	
	/**
	 * @param ac
	 * @param stereocenter
	 * @param propertyName
	 */
	private void addStereo(IAtomContainer ac, Set<IAtom> stereocenter, String propertyName) {
		List<IAtom> process = new ArrayList<IAtom>();
		for (IAtom center : stereocenter) {
			if (process.contains(center)) {
				continue;
			}
			String configuration = center.getProperty(propertyName);
			String type = null;
			List<IBond> con = ac.getConnectedBondsList(center);
			IChemObject focus = null;
			Set<IChemObject> ligands = new HashSet<IChemObject>();
			//Determine if the focus type is a bond or an atom
			for (IBond bond : con) {
				if (bond.getStereo() == IBond.Stereo.E || bond.getStereo() == IBond.Stereo.Z) {
					type = "BOND";
					//DB bond, look for the DB and get the other Atom
					//(for both case the other atom of the double bond is annotated as a stereocenter)
					for (IBond bond2 : con) {
						if (bond2.getOther(center).getProperty(IS_STEREOCENTER) != null) {
							focus = bond2;
							if (configuration.contains("DB")) {
								IAtom other = bond2.getOther(center);
								ligands.add(bond2);
								for (IBond bond3 : ac.getConnectedBondsList(other)) {
									if (bond3.getStereo() == IBond.Stereo.E || bond3.getStereo() == IBond.Stereo.Z) {
										ligands.add(bond3);
										break;
									}
								}
								process.add(other);
							}
							//EtendedCisTrans -> find other center
							if (configuration.contains("CT")) {
								//get Terminal Atoms in order to find the other ligand (other stereo bond)
								IAtom[] terminalAtoms = ExtendedCisTrans.findTerminalAtoms(ac, (IBond)focus);
								IAtom otherTerminal = terminalAtoms[0].equals(center) ? terminalAtoms[1] : terminalAtoms[0];
								for (IBond bond3 : ac.getConnectedBondsList(otherTerminal)) {
									if (bond3.getStereo() == IBond.Stereo.E || bond3.getStereo() == IBond.Stereo.Z) {
										ligands.add(bond3);
										break;
									}
								}
								process.add(otherTerminal);
							}
						}
						break;
					}
					break;
				}
				else if (bond.getStereo() == IBond.Stereo.UP || bond.getStereo() == IBond.Stereo.DOWN || 
						bond.getStereo() == IBond.Stereo.UP_INVERTED || bond.getStereo() == IBond.Stereo.DOWN_INVERTED) {
					//Atropoisomeric
					//DB bond, look for the DB and get the other Atom
					//(for both case the other atom of the double bond is annotated as a stereocenter)
					if (configuration.contains("AP")) {
						for (IBond bond2 : con) {
							if (bond2.getOther(center).getProperty(IS_STEREOCENTER) != null && 
									bond2.getOrder().equals(IBond.Order.DOUBLE)) {
								focus = bond2;
								type = "BOND";
								break;
							}
						}
					}
					else if (configuration.contains("TH") || configuration.contains("AL") || configuration.contains("SP") ||
							configuration.contains("TB") || configuration.contains("OH")) {
						focus = center;
						type = "ATOM";
						break;
					}
				}
			}
			if (type.equals("BOND")) {
				IBond[] newLigands = new IBond[ligands.size()];
				newLigands = ligands.toArray(newLigands);
				if (ligands.size() >= 2) {
					if (configuration.equals("@DB1")) {
						ac.addStereoElement(
							new DoubleBondStereochemistry((IBond)focus, newLigands, 
								IDoubleBondStereochemistry.Conformation.OPPOSITE));
					}
					else if (configuration.equals("@DB2")) {
						ac.addStereoElement(
							new DoubleBondStereochemistry((IBond)focus, newLigands, 
								IDoubleBondStereochemistry.Conformation.TOGETHER));
					}
					else if (configuration.equals("@CT1")) {
						ac.addStereoElement(
							new ExtendedCisTrans((IBond)focus, newLigands, 
									IStereoElement.OPPOSITE));
					}
					else if (configuration.equals("@CT2")) {
						ac.addStereoElement(
							new ExtendedCisTrans((IBond)focus, newLigands, 
									IStereoElement.TOGETHER));
					}
					else if (configuration.equals("@AP1")) {
						ligands.clear();
						IAtom begin = ((IBond)focus).getBegin();
						IAtom end = ((IBond)focus).getEnd();
						ligands.addAll(ac.getConnectedAtomsList(begin));
						if (ligands.size() == 2) {
							ligands.add(begin);
						}
						ligands.addAll(ac.getConnectedAtomsList(end));
						if (ligands.size() == 6) {
							ligands.add(begin);
						}
						ligands.remove(begin);
						ligands.remove(end);
						IAtom[] newLigands2 = new IAtom[ligands.size()];
						newLigands2 = ligands.toArray(newLigands2);
						ac.addStereoElement(
								new Atropisomeric((IBond)focus, newLigands2, IStereoElement.LEFT));	
					}
					else if (configuration.equals("@AP2")) {
						ligands.clear();
						IAtom begin = ((IBond)focus).getBegin();
						IAtom end = ((IBond)focus).getEnd();
						ligands.addAll(ac.getConnectedAtomsList(begin));
						if (ligands.size() == 2) {
							ligands.add(begin);
						}
						ligands.addAll(ac.getConnectedAtomsList(end));
						if (ligands.size() == 6) {
							ligands.add(begin);
						}
						ligands.remove(begin);
						ligands.remove(end);
						IAtom[] newLigands2 = new IAtom[ligands.size()];
						newLigands2 = ligands.toArray(newLigands2);
						ac.addStereoElement(
								new Atropisomeric((IBond)focus, newLigands2, IStereoElement.RIGHT));	
					}
				}
				
			}
			else if (type.equals("ATOM")) {
				List<IAtom> ligands2 = ac.getConnectedAtomsList((IAtom)focus);
				if (configuration.contains("TH")) {
					IAtom[] newLigands = new IAtom[4];
					// there is an implicit hydrogen (or lone-pair), the central atom is added
					if (ligands2.size() == 3) {
						for (int i = 0; i < ligands2.size(); i++) {
							newLigands[i] = ligands2.get(i);
						}
						newLigands[3] = (IAtom)focus;
					}
					else if (ligands2.size() == 4) {
						newLigands = ligands2.toArray(newLigands);
					}
					if (configuration.equals("@TH1") && ligands2.size() > 2) {
						ac.addStereoElement(
								new TetrahedralChirality((IAtom)focus, newLigands, 
										ITetrahedralChirality.Stereo.ANTI_CLOCKWISE));
					}
					else if (configuration.equals("@TH2") && ligands2.size() > 2) {
						ac.addStereoElement(
								new TetrahedralChirality((IAtom)focus, newLigands, 
										ITetrahedralChirality.Stereo.CLOCKWISE));
					}
				}
				else if (configuration.contains("AL")) {
					ligands2 = new ArrayList<IAtom>();
					//get Terminal Atoms
					IAtom[] terminalAtoms = ExtendedTetrahedral.findTerminalAtoms(ac, (IAtom)focus);
					//get the 4 peripheral atoms (connected by a single bond)
					for (IBond bond : ac.getConnectedBondsList(terminalAtoms[0])) {
						if (bond.getOrder().equals(IBond.Order.SINGLE)) {
							ligands2.add(bond.getOther(terminalAtoms[0]));
						}
					}
					// there is an implicit hydrogen (or lone-pair), the central atom is added
					if (ligands.size() == 1) {
						ligands2.add(terminalAtoms[0]);
					}
					for (IBond bond : ac.getConnectedBondsList(terminalAtoms[1])) {
						if (bond.getOrder().equals(IBond.Order.SINGLE)) {
							ligands2.add(bond.getOther(terminalAtoms[1]));
						}
					}
					// there is an implicit hydrogen (or lone-pair), the central atom is added
					if (ligands.size() == 3) {
						ligands2.add(terminalAtoms[1]);
					}
					IAtom[] newLigands = new IAtom[ligands2.size()];
					newLigands = ligands2.toArray(newLigands);
					
					if (configuration.equals("@AL1")) {
						ac.addStereoElement(
								new ExtendedTetrahedral((IAtom)focus, newLigands, 
										ITetrahedralChirality.Stereo.ANTI_CLOCKWISE));					
					}
					else if (configuration.equals("@AL2")) {
						ac.addStereoElement(
								new ExtendedTetrahedral((IAtom)focus, newLigands, 
										ITetrahedralChirality.Stereo.CLOCKWISE));
					}
				}
				else if (configuration.contains("SP")) {
					IAtom[] newLigands = new IAtom[4];
					// there is an implicit hydrogen (or lone-pair), the central atom is added
					if (ligands2.size() < 4) {
						for (int i = 0; i < ligands2.size(); i++) {
							newLigands[i] = ligands2.get(i);
						}
						for (int i = ligands2.size(); i < 4; i++) {
							newLigands[i] = (IAtom)focus;
						}
					}
					else {
						newLigands = ligands2.toArray(newLigands);
					}
					if (configuration.equals("@SP1")) {
						ac.addStereoElement(
								new SquarePlanar((IAtom)focus, newLigands, 1));	
					}
					else if (configuration.equals("@SP2")) {
						ac.addStereoElement(
								new SquarePlanar((IAtom)focus, newLigands, 2));	
					}
					else if (configuration.equals("@SP3")) {
						ac.addStereoElement(
								new SquarePlanar((IAtom)focus, newLigands, 3));	
					}
				}
				else if (configuration.contains("TB")) {
					IAtom[] newLigands = new IAtom[5];
					// there is an implicit hydrogen (or lone-pair), the central atom is added
					if (ligands2.size() < 5) {
						for (int i = 0; i < ligands2.size(); i++) {
							newLigands[i] = ligands2.get(i);
						}
						for (int i = ligands2.size(); i < 4; i++) {
							newLigands[i] = (IAtom)focus;
						}
					}
					else {
						newLigands = ligands2.toArray(newLigands);
					}
					if (configuration.equals("@TB1")) {
						ac.addStereoElement(
								new TrigonalBipyramidal((IAtom)focus, newLigands, IStereoElement.LEFT));	
					}
					else if (configuration.equals("@TB2")) {
						ac.addStereoElement(
								new TrigonalBipyramidal((IAtom)focus, newLigands, IStereoElement.RIGHT));	
					}
				}
				else if (configuration.contains("OH")) {
					IAtom[] newLigands = new IAtom[6];
					// there is an implicit hydrogen (or lone-pair), the central atom is added
					if (ligands2.size() < 6) {
						for (int i = 0; i < ligands2.size(); i++) {
							newLigands[i] = ligands2.get(i);
						}
						for (int i = ligands2.size(); i < 4; i++) {
							newLigands[i] = (IAtom)focus;
						}
					}
					else {
						newLigands = ligands2.toArray(newLigands);
					}
					if (configuration.equals("@OH1")) {
						ac.addStereoElement(
								new Octahedral((IAtom)focus, newLigands, IStereoElement.LEFT));	
					}
					else if (configuration.equals("@OH2")) {
						ac.addStereoElement(
								new Octahedral((IAtom)focus, newLigands, IStereoElement.RIGHT));	
					}
				}
			}
			process.add(center);
		}
	}
	
	/**
	 * @param bond
	 * @return
	 */
	private IBond shallowCopyBond(IBond bond) {
		IBond copy = new Bond();
		copy.setAtom(bond.getAtom(0), 0);
		copy.setAtom(bond.getAtom(1), 1);
		copy.setID(bond.getID());
		copy.setIsAromatic(bond.isAromatic());
		if (bond.isAromatic()) {
			copy.getBegin().setIsAromatic(true);
			copy.getEnd().setIsAromatic(true);
		}
		copy.setIsInRing(bond.isInRing());
		copy.setOrder(bond.getOrder());
		copy.setProperties(bond.getProperties());
		copy.setStereo(bond.getStereo());
		return copy;
	}
	
	/**
	 * @return
	 */
	private HashMap<String,Integer> makereverseConnectionTableAlphabet(){
		HashMap<String,Integer> result = new HashMap<String,Integer>();
		int cpt = 0;
		for (char alphabet1 = 'G'; alphabet1 <= 'Z'; alphabet1++) {
			for (char alphabet2 = 'G'; alphabet2 <= 'Z'; alphabet2++) {
				result.put(""+alphabet1+alphabet2, cpt);
				cpt++;
			}
		}
		return result;
	}

	public Set<IBond> getBondsCleaved() {
		return bondsCleaved;
	}

	public Set<IBond> getBondsFormed() {
		return bondsFormed;
	}

	public Set<IBond> getBondsOrder() {
		return bondsOrder;
	}

	public Set<IAtom> getReactionCenter() {
		return reactionCenter;
	}

	public List<String> getErrors() {
		return errors;
	}
}

//descending comparison res = (3,2,1)
class CompareByRep implements Comparator<IAtom> { 
	public int compare(IAtom a1, IAtom a2) { 
		if ((int) a1.getProperty("nRep") < (int) a2.getProperty("nRep"))
			return 1;
		else 
			return -1;
	} 
}
