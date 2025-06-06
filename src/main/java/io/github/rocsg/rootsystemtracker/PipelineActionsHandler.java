package io.github.rocsg.rootsystemtracker;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import io.github.rocsg.fijiyama.common.Bord;
import io.github.rocsg.fijiyama.common.DouglasPeuckerSimplify;
import io.github.rocsg.fijiyama.common.Pix;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.fijiyamaplugin.RegistrationAction;
import io.github.rocsg.fijiyama.registration.BlockMatchingRegistration;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.fijiyama.registration.Transform3DType;
import io.github.rocsg.fijiyama.rsml.Node;
import io.github.rocsg.fijiyama.rsml.Root;
import io.github.rocsg.fijiyama.rsml.RootModel;
import io.github.rocsg.rstutils.MorphoUtils;
import io.github.rocsg.topologicaltracking.CC;
import io.github.rocsg.topologicaltracking.ConnectionEdge;
import io.github.rocsg.topologicaltracking.RegionAdjacencyGraphPipeline;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import static io.github.rocsg.rootsystemtracker.PipelineParamHandler.configurePipelineParams;

public class PipelineActionsHandler {
	public static final int flagFinished=8;
	public static final int flagLastImage=200;
	public static final boolean proceedFullPipelineImageAfterImage=true;
	public static final int firstStepToDo=0;
	public static final int lastStepToDo=flagFinished;
	public static final int firstImageToDo=0;
	public static final int lastImageToDo=flagLastImage;//flagFinished;
	public static final int yMaxStamp=50;//TODO. It is relative value Y, after the crop
	public static Timer t;
	
	

	public static int[]selectFirstAndLast(PipelineParamHandler pph){
		if(VitiDialogs.getYesNoUI("Process everything box after box (select no to refine)?", "Process everything box after box (select no to refine)?"))return new int[] {0,flagFinished,0,pph.nbData-1,0};
		else{
			String[]actions=new String[] {"Step 0: setup part 1","Step 1:image stacking","Step 2: stack registration",
					"Step 3 : mask computation, leaves removal","Step 4: spatio-temporal segmentation",
					"Step 5 : graph computation","Step 6: RSML building until expertize","Step 7: RSML building after expertize", "Step 8: Movie building"}; 
			String[]order=new String[] {"Box after box","Step after step"};
			int[]vals=new int[5];
			GenericDialog gd= new GenericDialog("Expert mode for RootSystemTracker");
            gd.addMessage("Choose the steps to execute");
			gd.addChoice("First step to run",actions, actions[0]);
			gd.addChoice("Last step to run",actions, actions[0]);
	        gd.addMessage("Choose the indices of box to be processed (from 0 to "+(pph.nbData-1)+")");
    		gd.addNumericField("First box index to process",  0, 0 , 6 ,"");
			gd.addNumericField("Last box index to process", pph.nbData-1, 0, 6,"");
	        gd.addMessage("Choose the order : box after box (all steps) or step after step (all boxes)");
			gd.addChoice("Order",order, order[0]);
	        gd.showDialog();
	        if (gd.wasCanceled()) return new int[] {0,flagFinished,0,flagLastImage,0};	        
	        int st1=gd.getNextChoiceIndex();
	        int st2=gd.getNextChoiceIndex();
	        int im1=(int)Math.round(gd.getNextNumber());
	        int im2=(int)Math.round(gd.getNextNumber());
	        int ord=gd.getNextChoiceIndex();
	        if(st1<0)st1=0;
	        if(st2<0)st2=0;
	        if(st2>flagFinished)st1=flagFinished;
	        if(st1>st2)st1=st2;
	        if(im1<0)im1=0;
	        if(im2<0)im2=0;
	        if(im2>flagLastImage)st1=flagLastImage;
	        if(im1>im2)im1=im2;
	        return new int[] {st1,st2,im1,im2,ord};
		}
	}
	
	public static void goOnExperiment(PipelineParamHandler pph) {
		System.out.println("Going on  !");
		int[]vals=selectFirstAndLast(pph);
		int indFirstImageToDo=vals[2];
		int indLastImageToDo=vals[3];
		int indFirstStepToDo=vals[0];
		int indLastStepToDo=vals[1];
		int order=vals[4];
		IJ.showMessage("Params of processing=imgs "+indFirstImageToDo+"-"+indLastImageToDo+", Steps "+indFirstStepToDo+"-"+indLastStepToDo+", order "+order);
		t=new Timer();
		boolean rewriteNeeded=false;
		for(int in=indFirstImageToDo;in<=indLastImageToDo;in++)if(pph.imgSteps[in]>indFirstStepToDo)rewriteNeeded=true;
		if(rewriteNeeded) {
			if(VitiDialogs.getYesNoUI("Rewriting current step ?", "Some box to process are more advanced than step "+indFirstStepToDo+".\n Yes: recompute all the steps from step "+indFirstStepToDo+"  or   No: only compute missing steps")) {
				for(int in=indFirstImageToDo;in<=indLastImageToDo;in++)if(pph.imgSteps[in]>indFirstStepToDo)pph.imgSteps[in]=indFirstStepToDo;
				pph.writeParameters(false);				
			}
		}
		if(order==0) {
			for(int i=indFirstImageToDo;i<=Math.min(indLastImageToDo,pph.nbData-1);i++) {
				while(((pph.imgSteps[i]+1)>=indFirstStepToDo && pph.imgSteps[i]<=indLastStepToDo)) {
					doNextStep(i,pph);
				}				
			}
		}
		else {
			for(int s=indFirstStepToDo ; s<=indLastStepToDo; s++) {
				for(int i=indFirstImageToDo;i<=Math.min(indLastImageToDo,pph.nbData-1);i++) {
					if(pph.imgSteps[i]==(s-1)) doNextStep(i,pph);
				}				
			}					
		}
		IJ.log("Processing finished !");
	}
	
	public static void doNextStep(int indexImg,PipelineParamHandler pph) {
		System.out.println("Doing next step of img index "+indexImg);
		int stepToDo=pph.imgSteps[indexImg];
		if(pph.imgNames[indexImg].contains(Plugin_RootDatasetMakeInventory.codeTrash) && (stepToDo>1))pph.imgSteps[indexImg]++;
		else if(doStepOnImg(stepToDo,indexImg,pph))pph.imgSteps[indexImg]++;
		pph.writeParameters(false);
	}

	public static boolean doStepOnImg(int step,int indexImg,PipelineParamHandler pph) {
		//Where processing data is saved
		String outputDataDir=new File(pph.outputDir,pph.imgNames[indexImg]).getAbsolutePath();
		boolean executed=true;
		if(step==1) {//Stack data -O-
			t.print("Starting step 1, stacking -  on img index "+step+" : "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.stackData(indexImg,pph);
		}
		if(step==2) {//Registration
			t.print("Starting step 2, registration -  on img "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.registerSerie(indexImg,outputDataDir,pph);
		}
		if(step==3) {//Compute mask, find leaves falling in the ground and remove them
			t.print("Starting step 3, masking -  on img "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.computeMasksAndRemoveLeaves(indexImg,outputDataDir,pph);
		}
		if(step==4) {//Compute graph
			t.print("Starting step 4, space/time segmentation -  on img "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.spaceTimeMeanShiftSegmentation(indexImg,outputDataDir,pph);
		}
		if(step==5) {//Compute graph
			t.print("Starting step 5 -  on img "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.buildAndProcessGraph(indexImg, "",outputDataDir,pph);
		}
		if(step==6) {//RSML building
			t.print("Starting step 6 -  on img "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.computeRSMLUntilExpertize(indexImg, "",outputDataDir,pph);
		}
		if(step==7) {//RSML building
			t.print("Starting step 7 -  on img "+pph.imgNames[indexImg]);
			executed=PipelineActionsHandler.computeRSMLAfterExpertize(indexImg, "",outputDataDir,pph);
		}
		if(step==8) {//MovieBuilding -O-
			t.print("Starting step 8  -  on img "+pph.imgNames[indexImg]);
			executed=MovieBuilder.buildMovie(indexImg,outputDataDir,pph);
		}
		/*
		if(step==9) {//Phene extraction
			t.print("Starting step 9  -  on img "+pph.imgNames[indexImg]);
			executed=extractPhenes(indexImg,outputDataDir,pph);
		}
		*/
		return executed;
	}

	public static boolean stackData(int indexImg,PipelineParamHandler pph) {
		//Open the csv describing the experience
		String [][] csvDataExpe=VitimageUtils.readStringTabFromCsv( new File(pph.inventoryDir,"A_main_inventory.csv").getAbsolutePath() );
		String mainDataDir=csvDataExpe[4][1];
		System.out.println("Maindatadir="+mainDataDir);

		//Open the csv describing the box
		String outputDataDir=new File(pph.outputDir,pph.imgNames[indexImg]).getAbsolutePath();
		String [][] csvDataImg=VitimageUtils.readStringTabFromCsv( new File(pph.inventoryDir,pph.imgNames[indexImg]+".csv").getAbsolutePath() );
		int N=csvDataImg.length-1;

		//Open, stack and time stamp the corresponding images
		ImagePlus[]tabImg=new ImagePlus[N];
		for(int n=0;n<N;n++) {
			String date=csvDataImg[1+n][1];
			double hours=Double.parseDouble(csvDataImg[1+n][2]);
			IJ.log("Opening image "+new File (mainDataDir,csvDataImg[1+n][3]).getAbsolutePath());
			tabImg[n]=IJ.openImage( new File (mainDataDir,csvDataImg[1+n][3]).getAbsolutePath());
			tabImg[n].getStack().setSliceLabel(date+"_ = h0 + "+hours+" h", 1);
		}
		ImagePlus stack=VitimageUtils.slicesToStack(tabImg);
		if(stack==null || stack.getStackSize()==0)   { IJ.showMessage("In PipelineSteps.stackData : no stack imported ");return false; }		
		
		//Size conversion and saving. No bitdepth conversion to handle here, supposing that everything is 8-bit there		
		stack=VitimageUtils.resize(stack, stack.getWidth()/pph.subsamplingFactor, stack.getHeight()/pph.subsamplingFactor, stack.getStackSize());		
		IJ.saveAsTiff(stack, new File(outputDataDir,"11_stack.tif").getAbsolutePath());
		return true;
	}
	
	public static boolean registerSerie(int indexImg,String outputDataDir,PipelineParamHandler pph) {
		ImagePlus stack=IJ.openImage(new File(outputDataDir,"11_stack.tif").getAbsolutePath());
		int N=stack.getStackSize();
		ImagePlus imgInit2=stack.duplicate();
		ImagePlus imgInit=VitimageUtils.cropImage(imgInit2, pph.xMinCrop,pph.yMinCrop,0,pph.dxCrop,pph.dyCrop,N);
		ImagePlus imgOut=imgInit.duplicate();
		IJ.run(imgOut,"32-bit","");

		//Create mask
		ImagePlus mask=new Duplicator().run(imgInit,1,1,1,1,1,1);
		mask=VitimageUtils.nullImage(mask);
		mask=VitimageUtils.drawRectangleInImage(mask, pph.marginRegisterLeft,pph.marginRegisterUp,pph.dxCrop-pph.marginRegisterLeft-pph.marginRegisterRight,pph.dyCrop-1,255);
		IJ.saveAsTiff(mask, new File(outputDataDir,"20_mask_for_registration.tif").getAbsolutePath());
		
		ImagePlus []tabImg=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImg2=VitimageUtils.stackToSlices(imgInit);
		ImagePlus []tabImgSmall=VitimageUtils.stackToSlices(imgInit);
		ItkTransform []tr=new ItkTransform[N];
		ItkTransform []trComposed=new ItkTransform[N];
		for(int i=0;i<tabImgSmall.length;i++) {
			tabImgSmall[i]=VitimageUtils.cropImage(tabImgSmall[i], 0, 0,0, tabImgSmall[i].getWidth(),(tabImgSmall[i].getHeight()*2)/3,1);
		}

		//First step : daisy-chain rigid registration
		Timer t=new Timer();
		t.log("Starting registration");
		for(int n=0;(n<N-1);n++) {
			t.log("n="+n);
			ItkTransform trRoot=null;
			RegistrationAction regAct=new RegistrationAction().defineSettingsFromTwoImages(tabImg[n],tabImg[n+1],null,false);
			regAct.setLevelMaxLinear(pph.maxLinear);
			regAct.setLevelMinLinear(0);
			regAct.strideX=8;
			regAct.strideY=8;
			regAct.neighX=3;
			regAct.neighY=3;
			regAct.selectLTS=90;
			regAct.setIterationsBM(8);
			BlockMatchingRegistration bm= BlockMatchingRegistration.setupBlockMatchingRegistration(tabImgSmall[n+1], tabImgSmall[n], regAct);
			bm.mask=mask.duplicate();
		    bm.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm.minBlockVariance/=4;
		    boolean viewRegistrations=false;//Useful for debugging
			if(viewRegistrations) {
				bm.displayRegistration=2;
				bm.adjustZoomFactor(((512.0))/tabImg[n].getWidth());
				bm.flagSingleView=true;
			}
			bm.displayR2=false;
		    tr[n]=bm.runBlockMatching(trRoot, false);		
		    if(viewRegistrations) {
		    	bm.closeLastImages();
		    	bm.freeMemory();
		    }
		}
		
		for(int n1=0;n1<N-1;n1++) {
			trComposed[n1]=new ItkTransform(tr[n1]);
			for(int n2=n1+1;n2<N-1;n2++) {
				trComposed[n1].addTransform(tr[n2]);
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg[n1], tabImg[n1]);
		}
		ImagePlus result1=VitimageUtils.slicesToStack(tabImg);
		result1.setTitle("step 1");
		IJ.saveAsTiff(result1, new File(outputDataDir,"21_midterm_registration.tif").getAbsolutePath());

		
		
		
		
		//Second step : daisy-chain dense registration  
		ImagePlus result2=null;
		ArrayList<ImagePlus>listAlreadyRegistered=new ArrayList<ImagePlus>();
		listAlreadyRegistered.add(tabImg2 [N-1]);
		for(int n1=N-2;n1>=0;n1--) {
			ImagePlus imgRef=listAlreadyRegistered.get(listAlreadyRegistered.size()-1);
			RegistrationAction regAct2=new RegistrationAction().defineSettingsFromTwoImages(tabImg[0],tabImg[0],null,false);				
			regAct2.setLevelMaxNonLinear(1);
			regAct2.setLevelMinNonLinear(-1);
			regAct2.setIterationsBMNonLinear(4);
			regAct2.typeTrans=Transform3DType.DENSE;
			regAct2.strideX=4;
			regAct2.strideY=4;
			regAct2.neighX=2;
			regAct2.neighY=2;
			regAct2.bhsX-=3;
			regAct2.bhsY-=3;
			regAct2.sigmaDense/=6;
			regAct2.selectLTS=80;
			BlockMatchingRegistration bm2= BlockMatchingRegistration.setupBlockMatchingRegistration(imgRef, tabImg2[n1], regAct2);
			bm2.mask=mask.duplicate();
		    bm2.defaultCoreNumber=VitimageUtils.getNbCores();
		    bm2.minBlockVariance=10;
		    bm2.minBlockScore=0.10;
		    bm2.displayR2=false;
		    boolean viewRegistrations=false;
			if(viewRegistrations) {
				bm2.displayRegistration=2;
				bm2.adjustZoomFactor(512.0/tabImg[n1].getWidth());
			}

			trComposed[n1]=bm2.runBlockMatching(trComposed[n1], false);			

			if(viewRegistrations) {
			    bm2.closeLastImages();
			    bm2.freeMemory();
			}
			tabImg[n1]=trComposed[n1].transformImage(tabImg2[n1], tabImg2[n1]);
			listAlreadyRegistered.add(tabImg[n1]);
		}
		result2=VitimageUtils.slicesToStack(tabImg);
		result2.setTitle("Registered stack");
		IJ.saveAsTiff(result2, new File(outputDataDir,"22_registered_stack.tif").getAbsolutePath());
		return true;
	}	
	
	public static boolean computeMasksAndRemoveLeaves(int indexImg,String outputDataDir,PipelineParamHandler pph) {
		ImagePlus imgReg=IJ.openImage(new File(outputDataDir,"22_registered_stack.tif").getAbsolutePath());
		ImagePlus imgMask1=getMaskOfAreaInterestAtTime(imgReg, 1,false);
		IJ.saveAsTiff(imgMask1,new File(outputDataDir,"31_mask_at_t1.tif").getAbsolutePath());
		
		
		ImagePlus imgMaskN=getMaskOfAreaInterestAtTime(imgReg, imgReg.getStackSize(),false);
		IJ.saveAsTiff(imgMaskN,new File(outputDataDir,"32_mask_at_tN.tif").getAbsolutePath());

		ImagePlus imgMask2=	MorphoUtils.erosionCircle2D(imgMask1, 250);//TODO : give a geometrical meaning to 250
		imgMask2.setDisplayRange(0, 1);
		IJ.saveAsTiff(imgMask2, new File(outputDataDir,"33_mask_feuilles").getAbsolutePath());		//TODO give an adaptative threshold

		ImagePlus []imgsOut=removeLeavesFromSequence(imgReg, imgMask1, imgMask2);

		imgsOut[0].setDisplayRange(0, 255);//TODO give an adaptative threshold
		if(pph.isSplit()) {
			imgsOut[0]=removeSplitCentralLine(imgsOut[0]);
		}
		if(pph.isGaps()) {
			//TODO
		}
		IJ.saveAsTiff(imgsOut[0],new File(outputDataDir,"34_leaves_removed").getAbsolutePath());
		imgsOut[1].setDisplayRange(0, 1);//TODO give an adaptative threshold
		IJ.saveAsTiff(imgsOut[1],new File(outputDataDir,"35_mask_of_removed_leaves").getAbsolutePath());
		
		return true;
	}	

	public static ImagePlus removeSplitCentralLine(ImagePlus img){
		ImagePlus[] imgs=VitimageUtils.stackToSlices(img);
		int N=imgs.length;
		IJ.log(""+N);
		ImagePlus img2=MorphoUtils.dilationCircle2D(imgs[N-1], 2);
		ImagePlus img3=MorphoUtils.dilationLine2D(img2, 15, true);
		ImagePlus img4=VitimageUtils.thresholdImage(img3, 152, 256);
		ImagePlus imgTrench=VitimageUtils.getBinaryMaskUnary(img4, 127);
		ImagePlus imgRest=VitimageUtils.invertBinaryMask(imgTrench);
		ImagePlus trench=VitimageUtils.makeOperationBetweenTwoImages(img3, imgTrench, 2, false);

		for(int i=0;i<N;i++) {
			ImagePlus rest=VitimageUtils.makeOperationBetweenTwoImages(imgs[i], imgRest, 2, false);
			imgs[i]=VitimageUtils.makeOperationBetweenTwoImages(rest, trench, 1, false);
		}
		return VitimageUtils.slicesToStack(imgs);
	}
	
	public static boolean spaceTimeMeanShiftSegmentation(int indexImg,String outputDataDir,PipelineParamHandler pph) {
		ImagePlus imgIn=IJ.openImage(new File(outputDataDir,"34_leaves_removed.tif").getAbsolutePath());
		ImagePlus imgMask1=IJ.openImage(new File(outputDataDir,"31_mask_at_t1.tif").getAbsolutePath());
		ImagePlus imgMaskN=IJ.openImage(new File(outputDataDir,"32_mask_at_tN.tif").getAbsolutePath());
		ImagePlus imgMaskOfLeaves=IJ.openImage(new File(outputDataDir,"35_mask_of_removed_leaves.tif").getAbsolutePath());

		//Insert a first slice with no roots in it. That way, the roots already present will be detected as roots appearing at time 1 (for convention, as background has label 0)
		ImagePlus mire=computeMire(imgIn);
		imgIn=VitimageUtils.addSliceToImage(mire, imgIn);
		int threshRupt=25;
		int threshSlope=10;
		ImagePlus imgOut=projectTimeLapseSequenceInColorspaceCombined(imgIn, imgMask1,imgMaskN,imgMaskOfLeaves,threshRupt,threshSlope);
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, imgMaskN, 2, true);
		ImagePlus img2=VitimageUtils.thresholdImage(imgOut, 0.5, 100000);
		img2=VitimageUtils.connexeNoFuckWithVolume(img2, 1, 10000, 2000, 1E10, 4, 0, true);
		img2=VitimageUtils.thresholdImage(img2, 0.5, 1E8);
		img2=VitimageUtils.getBinaryMaskUnary(img2, 0.5);
		IJ.run(img2,"8-bit","");
		imgOut=VitimageUtils.makeOperationBetweenTwoImages(imgOut, img2, 2, true);
		IJ.run(imgOut,"Fire","");
		imgOut.setDisplayRange(0, pph.imgSerieSize[indexImg]+1);
		IJ.saveAsTiff(imgOut, new File(outputDataDir,"40_date_map.tif").getAbsolutePath());
		return true;
	}

	public static boolean buildAndProcessGraph(int indexImg, String inputDataDir,String outputDataDir,PipelineParamHandler pph) {
		ImagePlus imgDates=IJ.openImage( new File(inputDataDir,"40_date_map.tif").getAbsolutePath());
		RegionAdjacencyGraphPipeline.buildAndProcessGraphStraight(imgDates,outputDataDir,pph,indexImg);
		return true;
	}

	public static boolean computeRSMLUntilExpertize(int indexImg, String inputDataDir,String outputDataDir,PipelineParamHandler pph) {
		ImagePlus mask=IJ.openImage(new File(inputDataDir,"31_mask_at_t1.tif").getAbsolutePath());
		mask=MorphoUtils.dilationCircle2D(mask, 9);
		ImagePlus dates=IJ.openImage(new File(inputDataDir,"40_date_map.tif").getAbsolutePath());
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=RegionAdjacencyGraphPipeline.readGraphFromFile(new File(outputDataDir,"50_graph.ser").getAbsolutePath());
		ImagePlus distOut=MorphoUtils.getDistOut(dates,false);
		ImagePlus reg=IJ.openImage(new File(inputDataDir,"22_registered_stack.tif").getAbsolutePath());

		RootModel rm=RegionAdjacencyGraphPipeline.refinePlongementOfCCGraph(graph,distOut,pph,indexImg);
		rm.cleanWildRsml();
		rm.resampleFlyingRoots();
		rm.cleanNegativeTh();
		
		rm.writeRSML3D(new File(outputDataDir,"60_graph_no_backtrack.rsml").getAbsolutePath(), "",true,false);
		if(!pph.isSplit()) {
		backTrackPrimaries(new File(outputDataDir,"60_graph_no_backtrack.rsml").getAbsolutePath(),new File(outputDataDir,"61_graph.rsml").getAbsolutePath(),mask,reg,pph.toleranceDistanceForBeuckerSimplification);
		}
		else {
			try {FileUtils.copyFile(new File(outputDataDir,"60_graph_no_backtrack.rsml"),new File(outputDataDir,"61_graph.rsml"));} catch (IOException e) {e.printStackTrace();}
		}
		return true;
	}
		
	public static boolean computeRSMLAfterExpertize(int indexImg, String inputDataDir,String outputDataDir,PipelineParamHandler pph) {
		ImagePlus dates=IJ.openImage(new File(inputDataDir,"40_date_map.tif").getAbsolutePath());
		SimpleDirectedWeightedGraph<CC,ConnectionEdge> graph=RegionAdjacencyGraphPipeline.readGraphFromFile(new File(outputDataDir,"50_graph.ser").getAbsolutePath());
		ImagePlus reg=IJ.openImage(new File(inputDataDir,"22_registered_stack.tif").getAbsolutePath());

		RootModel rm=null;
		if(new File(outputDataDir,"61_graph_expertized.rsml").exists()) {
			rm=RootModel.RootModelWildReadFromRsml(new File(outputDataDir,"61_graph_expertized.rsml").getAbsolutePath());
		}
		else {
			rm=RootModel.RootModelWildReadFromRsml(new File(outputDataDir,"61_graph.rsml").getAbsolutePath());
		}
		ImagePlus skeletonTime=RegionAdjacencyGraphPipeline.drawDistanceOrTime(dates,graph,false,true,3);
		ImagePlus skeletonDay=RegionAdjacencyGraphPipeline.drawDistanceOrTime(dates,graph,false,true,2);
		ImagePlus allTimes=RegionAdjacencyGraphPipeline.drawDistanceOrTime(dates,graph,false,false,1);

		if(pph.memorySaving==0) {
			ImagePlus timeRSMLimg=createTimeSequenceSuperposition(reg,rm);
			IJ.saveAsTiff(timeRSMLimg, new File(outputDataDir,"62_rsml_2dt_rendered_over_image_sequence.tif").getAbsolutePath());
		}
		System.out.println(skeletonDay);
		System.out.println(pph==null ? "PPH Null" : "PPH not null");
		System.out.println(pph.imgSerieSize==null ? "TAB Null" : "Tab not null");
		System.out.println(pph.imgSerieSize.length);
		System.out.println(indexImg);
		skeletonDay.setDisplayRange(0,pph.imgSerieSize[indexImg]+1 );
		skeletonTime.setDisplayRange(0, pph.imgSerieSize[indexImg]+1);
		allTimes.setDisplayRange(0, pph.imgSerieSize[indexImg]+1);
		IJ.saveAsTiff(skeletonTime, new File(outputDataDir,"63_time_skeleton.tif").getAbsolutePath());
		IJ.saveAsTiff(skeletonDay, new File(outputDataDir,"64_day_skeleton.tif").getAbsolutePath());
		IJ.saveAsTiff(allTimes,  new File(outputDataDir,"65_times.tif").getAbsolutePath());
		return true;
	}

	public static boolean extractPhenes(int indexImg,String outputDataDir,PipelineParamHandler pph) {
		
		
		return true;
	}
	
	public static ImagePlus createTimeSequenceSuperposition(ImagePlus imgReg,RootModel rm){
		ImagePlus[]tabRes=VitimageUtils.stackToSlices(imgReg);
		for(int i=0;i<tabRes.length;i++) {
			ImagePlus imgRSML=rm.createGrayScaleImageWithTime(tabRes[i],1,false,(i+1),true,new boolean[] {true,true,true,false,true},new double[] {2,2});
			tabRes[i]=RGBStackMerge.mergeChannels(new ImagePlus[] {tabRes[i],imgRSML}, false);
			IJ.run(tabRes[i],"RGB Color","");
		}
		ImagePlus res=VitimageUtils.slicesToStack(tabRes);
		return res;
	}

	public static void backTrackPrimaries(String pathToInputRsml,String pathToOutputRsml,ImagePlus mask,ImagePlus imgRegT0,double toleranceDistToCentralLine) {
		RootModel rmInit=RootModel.RootModelWildReadFromRsml(pathToInputRsml);
		Root[]prRoots=rmInit.getPrimaryRoots();
		int X=mask.getWidth();
		//int Y=mask.getHeight();
		int xTolerance=X/20;

		for(Root r : prRoots) {
			System.out.println("\nDebugging Root ");
			System.out.println(r);
			//Identify the first coordinates
			Node oldFirst=r.firstNode;
			int xMid=(int) oldFirst.x;
			int yMid=(int) oldFirst.y;
			System.out.println("Identified coordinates start="+xMid+","+yMid);
			
			//Identify the mean height of region to attain in this area
			int upperPix=0;
			for(int i=yMid;i>=0;i--) {
				if(mask.getPixel(xMid, i)[0]>0)upperPix=i;
			}
			upperPix-=10;//TODO
			
			if(upperPix<0)upperPix=0;
			
			//Extract a rectangle around the first coordinate at time 0
			ImagePlus imgExtractMask=VitimageUtils.cropImage(mask, Math.max(0,xMid-xTolerance), Math.max(0,upperPix), 0, xTolerance*2+1, yMid-upperPix+1, 1);
			imgExtractMask.setDisplayRange(0, 1);
			ImagePlus imgExtract=VitimageUtils.cropImage(imgRegT0, Math.max(0,xMid-xTolerance), Math.max(0,upperPix), 0, xTolerance*2+1, yMid-upperPix+1, 1);
			
			//Extract a min djikstra path to this region
			GraphPath<Pix,Bord>graph=VitimageUtils.getShortestAndDarkestPathInImage(imgExtract,8,new Pix(xTolerance,0,0),new Pix (xTolerance,yMid-upperPix,0));
			List<Pix>liInit=graph.getVertexList();
			
			
			//Find in this path the last point that is not in the interest area
			int indFirst=0;
			for(int i=0;i<graph.getLength() ;i++) {
				Pix p=liInit.get(i);
//				System.out.println("Testing "+p+ " = "+imgExtractMask.getPixel(p.x, p.y)[0]);
				if(imgExtractMask.getPixel(p.x, p.y)[0]<1)indFirst=i;
			}
			indFirst+=7;//Fit to the mean
			if(indFirst>=graph.getLength()-1)continue;//No path to add
			List<Pix>liSecond=new ArrayList<Pix>();
			for(int i=indFirst;i<graph.getLength() ;i++) {
				liSecond.add( liInit.get(i) );
			}
			
			//subsample the new path
			List<Integer>liNull=new ArrayList<Integer>();
			List<Pix>list= DouglasPeuckerSimplify.simplify(liSecond,liNull ,toleranceDistToCentralLine);
			list.remove(list.size()-1);
			int x0=xMid-xTolerance;
			int y0=upperPix;
			
			//Insert the corresponding coordinates update time value along the root		
			Pix p=list.get(0);
			Node n=new Node(p.x+x0,p.y+y0,0,null,false);
			r.firstNode=n;
			for(int i=1;i<list.size()-1;i++) {				
				p=list.get(i);
				Node n2=new Node(p.x+x0,p.y+y0,0.01f,n,true);
				n=n2;
			}
			n.child=oldFirst;
			oldFirst.parent=n;
			r.updateNnodes();
			r.computeDistances();
			r.resampleFlyingPoints(rmInit.hoursCorrespondingToTimePoints);
		}
		rmInit.writeRSML3D(pathToOutputRsml, "", true,false);
	}
	
	//////////////////// HELPERS OF COMPUTEMASKS ////////////////////////
	public static ImagePlus computeMire(ImagePlus imgIn) {
		ImagePlus img=new Duplicator().run(imgIn,1,1,1,1,1,1);
		IJ.run(img, "Median...", "radius=9 stack");
		//img=concatPartsOfImages(img,imgIn,"Y",0.5); In case of, when will be the time
		return img;
	}

	// Remove the falling stem of arabidopsis from a time lapse sequence imgMask contains all the root system, and imgMask2 only the part that cannot have a arabidopsis stem (the lower part)
	//In this new version, we replace big elements of object with what was at last image
	public static ImagePlus[] removeLeavesFromSequence(ImagePlus imgInit,ImagePlus imgMaskRoot,ImagePlus imgMask2Init) {
		int factor=1;
		ImagePlus[]tabInit=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabTot=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabMaskOut=VitimageUtils.stackToSlices(imgInit);
		ImagePlus[]tabMaskIn=VitimageUtils.stackToSlices(imgInit);
		tabMaskOut[0]=VitimageUtils.nullImage(tabMaskOut[0]);
		tabMaskIn[0]=VitimageUtils.invertBinaryMask(tabMaskOut[0]);
		ImagePlus replacement=VitimageUtils.nullImage(tabInit[0]);
		
		ImagePlus imgMaskAerialNot=VitimageUtils.invertBinaryMask(imgMask2Init);
		for(int z=1;z<tabInit.length;z++) {
			//Get the mask of the big elements of object under the menisque
			ImagePlus img=VitimageUtils.makeOperationBetweenTwoImages(tabInit[z], imgMaskRoot, 2, true);
			img=MorphoUtils.dilationCircle2D(img, 2*factor);
			img=VitimageUtils.gaussianFiltering(img, 3*factor, 3*factor, 0);
			ImagePlus biggas=VitimageUtils.thresholdImage(img, -100, 120);
			
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMaskAerialNot.duplicate(), biggas, 2);
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(imgMaskRoot, tabMaskOut[z], 2);
			tabMaskOut[z]=MorphoUtils.dilationCircle2D(tabMaskOut[z], 2*factor);
			tabMaskOut[z].setDisplayRange(0, 1);
			tabMaskOut[z].setTitle(" "+z);
			
			//Combine this mask with the one of the previous image
			tabMaskOut[z]=VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z-1], tabMaskOut[z], 1);
			tabMaskOut[z]=VitimageUtils.connexe2dNoFuckWithVolume(tabMaskOut[z], 0.5, 1000, 1000, 1000000, 4, 0, true);
			tabMaskOut[z]=VitimageUtils.thresholdImage(tabMaskOut[z], 0.5, 1000);
			tabMaskOut[z]=VitimageUtils.getBinaryMaskUnary(tabMaskOut[z], 0.5);		
			
			tabMaskIn[z]=VitimageUtils.invertBinaryMask(tabMaskOut[z]);
			ImagePlus maskNewArea=VitimageUtils.getBinaryMaskUnary(VitimageUtils.binaryOperationBetweenTwoImages(tabMaskOut[z], tabMaskOut[z-1], 4), 0.5);		
			replacement=VitimageUtils.addition(replacement, VitimageUtils.multiply(maskNewArea,tabInit[z-1],false),false);

			ImagePlus imgPart1=VitimageUtils.makeOperationBetweenTwoImages(tabMaskIn[z], tabInit[z], 2, false);
			ImagePlus imgPart2=VitimageUtils.makeOperationBetweenTwoImages(tabMaskOut[z], replacement, 2, false);
			tabTot[z]=VitimageUtils.makeOperationBetweenTwoImages(imgPart1, imgPart2, 1, false);
		}
		ImagePlus img1=VitimageUtils.slicesToStack(tabTot);
		img1.setDisplayRange(0, 255);
		ImagePlus img2=VitimageUtils.slicesToStack(tabMaskOut);
		img2.setDisplayRange(0, 1);
		return new ImagePlus [] {img1,img2};
	}

	public static void main(String[] args) {
		String inputDataDir = null, outputDataDir = null, inventoryOutput = null, acqTimesStr = null;
		for (String arg : args) {
			if (arg.startsWith("--input=")) inputDataDir = arg.substring("--input=".length());
			if (arg.startsWith("--output=")) outputDataDir = arg.substring("--output=".length());
			if (arg.startsWith("--acqTimes=")) acqTimesStr = arg.substring("--acqTimes=".length());
		}
		System.out.println("inputDataDir="+inputDataDir);
		System.out.println("outputDataDir="+outputDataDir);
		System.out.println("acqTimesStr="+acqTimesStr);
		if (inputDataDir == null || outputDataDir == null ||  acqTimesStr == null) {
			System.out.println("Usage: java ... --input=PATH --output=PATH --inventoryOutput=PATH --acqTimes=CSV_LIST");
			System.exit(1);
		}
		// Parse acqTimes
		String[] tokens = acqTimesStr.split(",");
		double[] acqTimes1D = new double[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			acqTimes1D[i] = Double.parseDouble(tokens[i]);
		}
		double[][] acqTimes = new double[1][];
		acqTimes[0] = acqTimes1D;

		PipelineParamHandler pph = new PipelineParamHandler(inputDataDir, acqTimes);
		//computeMasksAndRemoveLeaves(0,outputDataDir, pph);
		//spaceTimeMeanShiftSegmentation(0,outputDataDir, pph);
		buildAndProcessGraph(0, inputDataDir,outputDataDir, pph);
		computeRSMLUntilExpertize(0, inputDataDir,outputDataDir, pph);
		//computeRSMLAfterExpertize(0, inputDataDir,outputDataDir, pph);
		System.exit(0);
	}

	private static void createOutputDirectory(String path) {
        File outputFolder = new File(path);
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        if (Objects.requireNonNull(outputFolder.list()).length > 0) {
            try {
                Files.walkFileTree(outputFolder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                outputFolder.mkdir();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }	


	public static ImagePlus getMaskOfAreaInterestAtTime(ImagePlus imgReg,int time,boolean debug) {
		ImagePlus imgMask1=new Duplicator().run(imgReg,1,1,time,time,1,1);
		if(debug)imgMask1.duplicate().show();
		imgMask1=getMenisque(imgMask1,debug);
		if(debug)imgMask1.duplicate().show();
		imgMask1=VitimageUtils.invertBinaryMask(imgMask1);
		imgMask1=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(imgMask1, 4, 1);
		IJ.run(imgMask1,"8-bit","");
		if(debug)imgMask1.duplicate().show();
		imgMask1=VitimageUtils.getBinaryMaskUnary(imgMask1,0.5);
		imgMask1.setDisplayRange(0, 1);
		if(debug)imgMask1.duplicate().show();
		return imgMask1;
	}
	
	
	/**
	 * Draw rectangle in image.
	 *
	 * @param imgIn the img in
	 * @param x0 the x 0
	 * @param y0 the y 0
	 * @param xf the xf
	 * @param yf the yf
	 * @param value the value
	 * @return the image plus
	 */
	/* Helper functions to watermark various things in 3d image : Strings, rectangles, cylinders... */		
	public static ImagePlus drawRectangleInImage(ImagePlus imgIn,int x0,int y0,int xf,int yf,float value) {
		if(imgIn.getType() != ImagePlus.GRAY32)return imgIn;
		ImagePlus img=new Duplicator().run(imgIn);
		int xM=img.getWidth();
		int zM=img.getStackSize();
		float[][] valsImg=new float[zM][];
		for(int z=0;z<zM;z++) {
			valsImg[z]=(float [])img.getStack().getProcessor(z+1).getPixels();
			for(int x=x0;x<=xf;x++) {
				for(int y=y0;y<=yf;y++) {
					valsImg[z][xM*y+x]=  value;
				}
			}
		}			
		return img;
	}

	
	//TODO : give a geometrical meaning to the various params
	public static ImagePlus getMenisque(ImagePlus img,boolean debug) {
		//Compute the difference between a horizontal opening and a vertical opening
		int factor=1;
		if(debug) {ImagePlus im=img.duplicate();im.setTitle("Init");im.show();IJ.showMessage("Original image for get menisque");}
		ImagePlus img2=MorphoUtils.dilationLine2D(img, 8*factor,false);
		if(debug) {ImagePlus im2=img2.duplicate();im2.setTitle("Im2");im2.show();IJ.showMessage("After vertical dilation");}
		img2=MorphoUtils.erosionLine2D(img2, 8*factor,false);
		if(debug) {ImagePlus im25=img2.duplicate();im25.setTitle("Im25");im25.show();IJ.showMessage("After vertical erosion");}
		ImagePlus img3=MorphoUtils.dilationLine2D(img, 8*factor,true);
		if(debug) {ImagePlus im3=img3.duplicate();im3.setTitle("Im3");im3.show();IJ.showMessage("After horizontal dilation");}
		img3=MorphoUtils.erosionLine2D(img3, 8*factor,true);
		if(debug) {ImagePlus im35=img3.duplicate();im35.setTitle("Im35");im35.show();IJ.showMessage("After horizontal erosion");}
		ImagePlus img4=VitimageUtils.makeOperationBetweenTwoImages(img2, img3, 4, true);
		img4=drawRectangleInImage(img4, 0, 0, img4.getWidth(), yMaxStamp, 0);
		if(debug) {ImagePlus im4=img4.duplicate();im4.setTitle("Im4");im4.show();IJ.showMessage("After diff of 2 and 3 and removal of the stamp qr possible place");}
		
				
		//Open this difference, binarize and dilate, then select the biggest CC, and dilate it
		ImagePlus img5=MorphoUtils.dilationLine2D(img4, 100*factor,true);
		img5=MorphoUtils.erosionLine2D(img5, 15*factor,true);
		if(debug) {ImagePlus im5=img5.duplicate();im5.setTitle("Im5");im5.show();IJ.showMessage("After closing horizontally");}
		ImagePlus img6=VitimageUtils.thresholdImage(img5, 20, 500);
		if(debug) {ImagePlus im6=img6.duplicate();im6.setTitle("Im6");im6.show();IJ.showMessage("After thresholding from 20 to 500");}
		img6=MorphoUtils.dilationLine2D(img6, 50, true);
		img6=MorphoUtils.dilationLine2D(img6, 2*factor,true);
		img6=MorphoUtils.dilationLine2D(img6, 1*factor,false);
		img6=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img6, 4, 1);
		img6=MorphoUtils.dilationLine2D(img6, 3*factor,false);
		IJ.run(img6,"8-bit","");
		if(debug) {ImagePlus im8=img6.duplicate();im8.setTitle("Im8");im8.show();IJ.showMessage("After many dilations and selection of best component");}
		return img6;
	}
	

	
	
	//////////////////// HELPERS OF SPACETIMEMEANSHIFTSEGMENTATION ////////////////////////
	public static ImagePlus projectTimeLapseSequenceInColorspaceCombined(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOfLeaves,int thresholdRupture,int thresholdSlope) {
		//imgSeq.show();
		IJ.run(imgSeq, "Gaussian Blur...", "sigma=0.8");
		//IJ.run(imgSeq, "Mean...", "radius=1 stack");
		ImagePlus result1=projectTimeLapseSequenceInColorspaceMaxRuptureDown(imgSeq,interestMask1,interestMaskN,maskOfLeaves,thresholdRupture);
		ImagePlus result2=projectTimeLapseSequenceInColorspaceMaxSlope(imgSeq,interestMask1,interestMaskN,thresholdSlope);
		//result1.show();
		//result1.setTitle("result1Rupt");
		//result2.show();
		//VitimageUtils.waitFor(5000000);
		ImagePlus out=VitimageUtils.thresholdImage(result1, -0.5, 0.5);
		out=VitimageUtils.invertBinaryMask(out);
		ImagePlus mask=VitimageUtils.getBinaryMaskUnary(out, 0.5);
		result2=VitimageUtils.makeOperationBetweenTwoImages(result2, mask, 2, false);
		return result2;
	}

	public static ImagePlus projectTimeLapseSequenceInColorspaceMaxSlope(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,int threshold) {
		int N=imgSeq.getStackSize();
		ImagePlus[]imgTab=VitimageUtils.stackToSlices(imgSeq);
		ImagePlus[]imgs=new ImagePlus[N];
		
		for(int i=0;i<N-1;i++) {
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgTab[i], imgTab[i+1], 4, true);
			imgs[i+1]=VitimageUtils.makeOperationBetweenTwoImages(imgs[i+1],i==0 ? interestMask1:interestMaskN, 2, true);
		}
		imgs[0]=VitimageUtils.nullImage(imgs[1]);
		ImagePlus res=VitimageUtils.indMaxOfImageArrayDouble(imgs,threshold);
		return res;
	}

	public static ImagePlus projectTimeLapseSequenceInColorspaceMaxRuptureDown(ImagePlus imgSeq,ImagePlus interestMask1,ImagePlus interestMaskN,ImagePlus maskOutLeaves,int threshold) {
		ImagePlus[]tab=VitimageUtils.stackToSlices(imgSeq);
		IJ.run(maskOutLeaves,"32-bit","");
		ImagePlus[]tabLeavesOut=VitimageUtils.stackToSlices(maskOutLeaves);
		for(int i=0;i<tab.length;i++) {
			tab[i]=VitimageUtils.makeOperationBetweenTwoImages(tab[i],i<2 ? interestMask1 : interestMaskN, 2, true);
		}
		ImagePlus res=indRuptureDownOfImageArrayDouble(tab,tabLeavesOut,threshold);
		return res;
	}

	public static ImagePlus indRuptureDownOfImageArrayDouble(ImagePlus []imgs,ImagePlus []maskLeavesOut,int minThreshold) {
		int xM=imgs[0].getWidth();
		int yM=imgs[0].getHeight();
		int zM=imgs[0].getStackSize();
		ImagePlus retInd=VitimageUtils.nullImage(imgs[0].duplicate());
		float[]valsInd;
		float[][]valsImg=new float[imgs.length][];
		float[][]valsMask=new float[imgs.length][];
		double[]valsToDetect;
		double[]valsToMask;
		for(int z=0;z<zM;z++) {
			valsInd=(float [])retInd.getStack().getProcessor(z+1).getPixels();
			for(int i=0;i<imgs.length;i++) {
				valsImg[i]=(float [])imgs[i].getStack().getProcessor(z+1).getPixels();
				valsMask[i]=(float [])maskLeavesOut[((i<2) ? 0 : i-1)].getStack().getProcessor(z+1).getPixels();
			}
			for(int x=0;x<xM;x++) {
				for(int y=0;y<yM;y++) {
					int last=0;
					valsToDetect=new double[imgs.length];
					valsToMask=new double[imgs.length];
					for(int i=0;i<imgs.length;i++) {
						valsToDetect[i]=valsImg[i][xM*y+x];
						valsToMask[i]=valsMask[i][xM*y+x];
						if(valsToMask[i]<1)last=i;
					}
					boolean blabla=false;
					if(x==377 && y==133)blabla=true;
					double[]newTab=new double[last+1];
					for(int i=0;i<=last;i++) {
						newTab[i]=valsToDetect[i];
					}
					int rupt=ruptureDetectionDown(newTab, minThreshold,blabla);
					valsInd[xM*y+x]=rupt; 
				}			
			}
		}
		return retInd;
	}

	//Return the index which is the first point of the second distribution
	public static int ruptureDetectionDown(double[]vals,double threshold,boolean blabla) {
		int indMax=0;
		double diffMax=-10000000;
		int N=vals.length;
		for(int i=1;i<N;i++) {
			double m1=meanBetweenIncludedIndices(vals, 0, i-1);
			double m2=meanBetweenIncludedIndices(vals, i, N-1);
			double diff=m1-m2;
			if(diff>diffMax) {
				indMax=i;
				diffMax=diff;
			}
			if(blabla) {
//				System.out.println("Apres i="+i+" : indMax="+indMax+" diffMax="+diffMax+" et on avait m1="+m1+" et m2="+m2);
			}
		}		
		return (diffMax>threshold ? indMax : 0);
	}
	
	public static double meanBetweenIncludedIndices(double[]tab,int ind1,int ind2) {
		double tot=0;
		for(int i=ind1;i<=ind2;i++)tot+=tab[i];
		return (tot/(ind2-ind1+1));
	}
				


}
