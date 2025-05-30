package io.github.rocsg.rootsystemtracker;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import ij.IJ;

public class PipelineParamHandler {
	String currentVersion="1.0";
	String pathToParameterFile="";
	double movieTimeStep=1;//hours per keyframe
	String inventoryDir="";
	String outputDir="";
	int MAX_NUMBER_IMAGES=100000;
	int nMaxParams=100+MAX_NUMBER_IMAGES;
	int nParams=0;
	private String[][] params;
	public final String mainNameCsv="InfoSerieRootSystemTracker.csv";
	final static int NO_PARAM_INT=-999999999;
	final static double NO_PARAM_DOUBLE=-99999999;
	public int numberPlantsInBox=5;
	int nbData=1;
	public int sizeFactorForGraphRendering=6;
	int minSizeCC=5;
	double rootTissueIntensityLevel=30;
	double backgroundIntensityLevel=130;
	double maxSpeedLateral=33;//Defined as the number of pixels per typical timestep
	double meanSpeedLateral=10;//Defined as the number of pixels per typical timestep
	double typicalSpeed=12;//pixels/hour   TYPICAL_SPEED=100/8;//pixels/timestep. pix=19µm , timestep=8h, meaning TYPICAL=237 µm/h
	double penaltyCost=0.5;
	double nbMADforOutlierRejection=25;
	double minDistanceBetweenLateralInitiation=4;
	double minLateralStuckedToOtherLateral=30;
	public int memorySaving=0;//if 1, don't save very big debug images;
	static int xMinCrop=122;
	static int yMinCrop=212;
	static int dxCrop=1348;
	static int dyCrop=1166;
	static int maxLinear=4;
	String typeExp="Simple";
	public static int subsamplingFactor=4;
	static int marginRegisterLeft=12;
	static int marginRegisterUp=135;
	static int marginRegisterRight=0;
	static int marginRegisterDown=0;
	boolean applyFullPipelineImageAfterImage=true;
	public double toleranceDistanceForBeuckerSimplification=0.9;
	String[]imgNames;
	int[]imgSteps;
	public String[] imgTimes;
	public int[] imgSerieSize;
	private double[][] acqTimes;
	public int originalPixelSize=19;//µm
	private String unit="µm";
	public double typicalHourDelay=8;
	public int xMinTree=90;//TODO
	public int xMaxTree=1220;//TODO
	
	public static void main(String[]arg) {
	}
	public static void configurePipelineParams(Map<String, String> config) {
		PipelineParamHandler.subsamplingFactor = Integer.parseInt(config.getOrDefault("scalingFactor", "4"));
        PipelineParamHandler.xMinCrop = Integer.parseInt(config.getOrDefault("xMinCrop", "0"));
        PipelineParamHandler.dxCrop = Integer.parseInt(config.getOrDefault("dxCrop", "2305"));
        PipelineParamHandler.yMinCrop = Integer.parseInt(config.getOrDefault("yMinCrop", "0"));
        PipelineParamHandler.dyCrop = Integer.parseInt(config.getOrDefault("dyCrop", "2108"));
        PipelineParamHandler.marginRegisterLeft = Integer.parseInt(config.getOrDefault("marginRegisterLeft", "0"));
        PipelineParamHandler.marginRegisterUp = Integer.parseInt(config.getOrDefault("marginRegisterUp", "0"));
        PipelineParamHandler.marginRegisterDown = Integer.parseInt(config.getOrDefault("marginRegisterDown", "0"));
	}
    
	
	public PipelineParamHandler() {
	}
	
	public void setParameters(String parametersFile) {
		this.pathToParameterFile=parametersFile;
		readParameters();
	}
	

	public void runCleaningAssistant(String inventoryDir){
		String[][]dataClean=VitimageUtils.readStringTabFromCsv(new File(inventoryDir,"NOT_FOUND.csv").getAbsolutePath());
		IJ.showMessage("Please open an explorer window to help the cleaning");
		
	}
	
	public PipelineParamHandler(String inventoryDir,String outputDir) {
		if(new File(inventoryDir,"NOT_FOUND.csv").exists()) {
			IJ.showMessage("Warning. Found a NOT_FOUND.csv in dir "+inventoryDir+" . The cleaning assistant will open now.");
			runCleaningAssistant(inventoryDir);
		}

		this.inventoryDir=inventoryDir.replace("\\","/");;
		this.outputDir=outputDir.replace("\\","/");;
		this.pathToParameterFile=new File(outputDir,mainNameCsv).getAbsolutePath().replace("\\","/");; 
		
		if(new File(this.pathToParameterFile).exists())   { //Reading parameters from an existing file
			readParameters();                                  
		}
		else                                              { //Proposing default parameters, and the possibility to edit them
			getParametersForNewExperiment(); 
			writeParameters(true); 
			IJ.showMessage("Please edit parameters if needed, save file, then click ok.\n File="+this.pathToParameterFile +"" );
			readParameters();
			for(String imgName : getImgNames())new File(outputDir,VitimageUtils.withoutExtension(imgName)).mkdirs();
		}
	}

	public PipelineParamHandler(String path) {
		outputDir=path.replace("\\","/");
		readParameters();
	}

	public PipelineParamHandler(String path, double[][] acqTimes) {
		outputDir=path.replace("\\","/");
		readParameters(acqTimes);
	}

	public boolean isSplit() {
		return typeExp.contains("Split_V01");
	}
	
	public boolean isGaps() {
		return typeExp.contains("Gaps_V01");
	}
	
	public String[]getImgNames(){
		return imgNames;
	}

	public void readParameters(double[][] acqTimes) {
		System.out.println(new File(outputDir,mainNameCsv).getAbsolutePath());
		params=VitimageUtils.readStringTabFromCsv(new File(outputDir,mainNameCsv).getAbsolutePath().replace("\\","/"));
		IJ.log("The main CSV is opened with name : |"+new File(outputDir,mainNameCsv).getAbsolutePath().replace("\\","/")+"|");
		inventoryDir=getString("inventoryDir");
		xMinCrop=getInt("xMinCrop");
		yMinCrop=getInt("yMinCrop");
		dxCrop=getInt("dxCrop");
		dyCrop=getInt("dyCrop");
		typeExp=getString("typeExp");
		outputDir=getString("outputDir");
		movieTimeStep=getDouble("movieTimeStep");
		numberPlantsInBox=getInt("numberPlantsInBox");
		minSizeCC=getInt("minSizeCC");
		originalPixelSize=getInt("originalPixelSize");
		unit=getString("unit");
		sizeFactorForGraphRendering=getInt("sizeFactorForGraphRendering");
		rootTissueIntensityLevel=getDouble("rootTissueIntensityLevel");
		backgroundIntensityLevel=getDouble("backgroundIntensityLevel");
		minDistanceBetweenLateralInitiation=getDouble("minDistanceBetweenLateralInitiation");
		minLateralStuckedToOtherLateral=getDouble("minLateralStuckedToOtherLateral");
		maxSpeedLateral=getDouble("maxSpeedLateral");
		meanSpeedLateral=getDouble("meanSpeedLateral");
		typicalSpeed=getDouble("typicalSpeed");
		penaltyCost=getDouble("penaltyCost");
		nbMADforOutlierRejection=getDouble("nbMADforOutlierRejection");
		subsamplingFactor=getInt("subsamplingFactor");
		nbData=getInt("nbData");
		typicalHourDelay=getDouble("typicalHourDelay");

		imgNames=new String[nbData];
		imgSteps=new int[nbData];
		this.acqTimes=new double[nbData][0];
		imgSerieSize=new int[nbData];

		for(int i=0;i<nbData;i++) {
			imgNames[i]=getString("Img_"+i+"_name");
			imgSteps[i]=getInt("Img_"+i+"_step");
			IJ.log("We have inventoryDir="+inventoryDir);
			IJ.log("We have outputDir="+outputDir);
			System.out.println("And making inventory of |"+new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/")+"|");
			IJ.log("Did inventory of "+(new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/")));
			IJ.log("Testing "+(new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/")));
			/*String[][]paramsImg=VitimageUtils.readStringTabFromCsv(new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/") );
			IJ.log("And the String tab initialized is null ? "+(paramsImg==null));
			IJ.log("Or it has a number of lines = "+(paramsImg.length));
			IJ.log("Or imgSerieSize is null ? "+(imgSerieSize==null));
			IJ.log("Or imgSerieSize len is not good ? ="+(imgSerieSize.length));*/
			imgSerieSize[i]= acqTimes[i].length;
			this.acqTimes[i]=new double[imgSerieSize[i]];
			/*for(int j=0;j<imgSerieSize[i];j++) {
				acqTimes[i][j]=Double.parseDouble(paramsImg[j+1][2]);
			}*/
			this.acqTimes = acqTimes;

		}

		int ind=0;
		double sum=0;
		for(int i=0;i<nbData;i++) {
			for(int j=1;j<imgSerieSize[i];j++) {
				sum+=acqTimes[i][j]-acqTimes[i][j-1];
				ind++;
			}
		}
		this.typicalHourDelay=sum/ind;

	}

	public void readParameters() {
		System.out.println(new File(outputDir,mainNameCsv).getAbsolutePath());
		params=VitimageUtils.readStringTabFromCsv(new File(outputDir,mainNameCsv).getAbsolutePath().replace("\\","/"));
		IJ.log("The main CSV is opened with name : |"+new File(outputDir,mainNameCsv).getAbsolutePath().replace("\\","/")+"|");
		inventoryDir=getString("inventoryDir");
		xMinCrop=getInt("xMinCrop");
		yMinCrop=getInt("yMinCrop");
		dxCrop=getInt("dxCrop");
		dyCrop=getInt("dyCrop");
		typeExp=getString("typeExp");
		outputDir=getString("outputDir");
		movieTimeStep=getDouble("movieTimeStep");
		numberPlantsInBox=getInt("numberPlantsInBox");
		minSizeCC=getInt("minSizeCC");
		originalPixelSize=getInt("originalPixelSize");
		unit=getString("unit");
		sizeFactorForGraphRendering=getInt("sizeFactorForGraphRendering");
		rootTissueIntensityLevel=getDouble("rootTissueIntensityLevel");
		backgroundIntensityLevel=getDouble("backgroundIntensityLevel");
		minDistanceBetweenLateralInitiation=getDouble("minDistanceBetweenLateralInitiation");
		minLateralStuckedToOtherLateral=getDouble("minLateralStuckedToOtherLateral");
		maxSpeedLateral=getDouble("maxSpeedLateral");
		meanSpeedLateral=getDouble("meanSpeedLateral");
		typicalSpeed=getDouble("typicalSpeed");
		penaltyCost=getDouble("penaltyCost");
		nbMADforOutlierRejection=getDouble("nbMADforOutlierRejection");
		subsamplingFactor=getInt("subsamplingFactor");
		nbData=getInt("nbData");
		typicalHourDelay=getDouble("typicalHourDelay");

		imgNames=new String[nbData];
		imgSteps=new int[nbData];
		acqTimes=new double[nbData][0];
		imgSerieSize=new int[nbData];

		for(int i=0;i<nbData;i++) {
			imgNames[i]=getString("Img_"+i+"_name");
			imgSteps[i]=getInt("Img_"+i+"_step");
			IJ.log("We have inventoryDir="+inventoryDir);
			IJ.log("We have outputDir="+outputDir);
			System.out.println("And making inventory of |"+new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/")+"|");
			IJ.log("Did inventory of "+(new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/")));
			IJ.log("Testing "+(new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/")));
			String[][]paramsImg=VitimageUtils.readStringTabFromCsv(new File(inventoryDir,imgNames[i]+".csv").getAbsolutePath().replace("\\","/") );
			IJ.log("And the String tab initialized is null ? "+(paramsImg==null));
			IJ.log("Or it has a number of lines = "+(paramsImg.length));
			IJ.log("Or imgSerieSize is null ? "+(imgSerieSize==null));
			IJ.log("Or imgSerieSize len is not good ? ="+(imgSerieSize.length));
			imgSerieSize[i]=paramsImg.length-1;
			acqTimes[i]=new double[imgSerieSize[i]];
			for(int j=0;j<imgSerieSize[i];j++) {
				acqTimes[i][j]=Double.parseDouble(paramsImg[j+1][2]);
			}

		}

		int ind=0;
		double sum=0;
		for(int i=0;i<nbData;i++) {
			for(int j=1;j<imgSerieSize[i];j++) {
				sum+=acqTimes[i][j]-acqTimes[i][j-1];
				ind++;
			}
		}
		this.typicalHourDelay=sum/ind;

	}
	
	public void setAcqTimesForTest(double[][] tab) {
		this.acqTimes=tab;
	}
	
	public void getParametersForNewExperiment(){
		System.out.println(inventoryDir);
		System.out.println(new File(inventoryDir).exists());
		for(String s : Objects.requireNonNull(new File(inventoryDir).list()))System.out.println(s);
		nbData= Objects.requireNonNull(new File(inventoryDir).list()).length-1;
		if(nbData>MAX_NUMBER_IMAGES) {
			IJ.showMessage("Critical warning : number of images is too high : "+nbData+" > "+MAX_NUMBER_IMAGES);
		}
	}

	public double[]getHoursExtremities(int indexBox){
		double[]ret=new double[acqTimes[indexBox].length+1];
        if (acqTimes[indexBox].length - 1 >= 0)
            System.arraycopy(acqTimes[indexBox], 1, ret, 2, acqTimes[indexBox].length - 1);
		ret[0]=ret[1]-this.typicalHourDelay;
		return ret;
	}
	
	public double[]getHours(int indexBox){
		double[]ret=new double[acqTimes[indexBox].length+1];
		for(int i=1;i<acqTimes[indexBox].length;i++) {
			ret[i+1]=acqTimes[indexBox][i]*0.5+acqTimes[indexBox][i-1]*0.5;
		}
		double delta0=ret[3]-ret[2];
		ret[1]=ret[2]-delta0;
		ret[0]=ret[1]-delta0;
		return ret;
	}
	
	public double getMaxSpeedLateral() {
		return getDouble("maxSpeedLateral");
	}

	public double getMinDistanceBetweenLateralInitiation() {
		return getDouble("minDistanceBetweenLateralInitiation");
	}
	
	public double getMinLateralStuckedToOtherLateral() {
		return getDouble("minLateralStuckedToOtherLateral");
	}
	public double getMeanSpeedLateral() {
		return getDouble("meanSpeedLateral");
	}
	
	public double getMovieTimeStep() {
		double d= getDouble("movieTimeStep");
		if (d<0)return 1;
		return d;
	}
	
	public void addAllParametersToTab() {
		params=new String[40+2*nbData][3];
		nParams=0;
		addParam("## Parameters for RootSystemTracker experiment ##","","");
		addParam("inventoryDir",inventoryDir,"");
		addParam("outputDir",outputDir ,"");
	
		addParam("nbData",nbData ,"");
		addParam("numberPlantsInBox",numberPlantsInBox ,"");
		addParam("minSizeCC",minSizeCC ,"");
		addParam("sizeFactorForGraphRendering",sizeFactorForGraphRendering ,"");
		
		addParam("rootTissueIntensityLevel",rootTissueIntensityLevel ,"");
		addParam("backgroundIntensityLevel",backgroundIntensityLevel ,"");
		addParam("minDistanceBetweenLateralInitiation",minDistanceBetweenLateralInitiation ,"");
		addParam("minLateralStuckedToOtherLateral",minLateralStuckedToOtherLateral ,"");
		addParam("maxSpeedLateral",maxSpeedLateral ,"");
		addParam("meanSpeedLateral", meanSpeedLateral,"");
		addParam("typicalSpeed",typicalSpeed ,"");
		addParam("penaltyCost", penaltyCost,"");
		addParam("typicalSpeed", typicalSpeed,"");
		addParam("nbMADforOutlierRejection",nbMADforOutlierRejection ,"");
		addParam("xMinCrop",xMinCrop,"");
		addParam("yMinCrop",yMinCrop,"");
		addParam("dxCrop",dxCrop,"");
		addParam("dyCrop",dyCrop,"");
		addParam("maxLinear",maxLinear,"Used to define the max level");
		addParam("subsamplingFactor",subsamplingFactor ,"");
		addParam("originalPixelSize",originalPixelSize,"");
		addParam("unit",unit,"");
		addParam("typicalHourDelay",typicalHourDelay,"");
		addParam("typeExp",typeExp,"-");
		addParam("movieTimeStep",movieTimeStep,"-");
	}
	
	public void writeParameters(boolean firstWrite) {

		addAllParametersToTab();
		
		if(firstWrite) {
			imgNames=new String[nbData];
			imgSteps=new int[nbData];
			imgTimes=new String[nbData];
			imgSerieSize=new int[nbData];
			String[]listImgs=new File(inventoryDir).list(new FilenameFilter() {		
				@Override
				public boolean accept(File arg0, String arg1) {
					if(arg1.equals("A_main_inventory.csv"))return false;
					if(arg1.equals("NOT_FOUND.csv"))return false;
					return true;
				}
			});
			Arrays.sort(listImgs);
			
			for(int i=0;i<nbData;i++) {
				imgNames[i]=listImgs[i].replace(".csv", "").replace("\\","/");
				imgSteps[i]=0;
			}
		}
		for(int i=0;i<nbData;i++) {
			addParam("Img_"+i+"_name",imgNames[i],"");
			addParam("Img_"+i+"_step",imgSteps[i],"");
		}
		VitimageUtils.writeStringTabInCsv2(params,new File(outputDir,mainNameCsv).getAbsolutePath().replace("\\","/"));		
	}
	
	
	public String getString(String tit) {
		for(int i=0;i<params.length;i++)if(params[i][0].equals(tit))return params[i][1].replace("\\", "/");
//		IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
		return "";
	}
	
	public double getDouble(String tit) {
		for(int i=0;i<params.length;i++)if(params[i][0].equals(tit))return Double.parseDouble( params[i][1] );
		IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
		return NO_PARAM_DOUBLE;
	}

	public int getInt(String tit) {
		for(int i=0;i<params.length;i++)if(params[i][0].equals(tit))return Integer.parseInt( params[i][1] );
		IJ.showMessage("Parameter not found : "+tit+" in param file of "+outputDir);
		return NO_PARAM_INT;
	}
	
	public void addParam(String tit,String val,String info){
		params[nParams++]=new String[] {tit,val,info};
	}

	public void addParam(String tit,double val,String info){
		params[nParams++]=new String[] {tit,""+val,info};
	}

	public void addParam(String tit,int val,String info){
		params[nParams++]=new String[] {tit,""+val,info};
	}

	public double getTypicalSpeed() {
		return getDouble("typicalSpeed");
	}
	
}
