/**
*
*  Tarsos is developed by Joren Six at 
*  The Royal Academy of Fine Arts & Royal Conservatory,
*  University College Ghent,
*  Hoogpoort 64, 9000 Ghent - Belgium
*
**/
package be.hogent.tarsos.ui.pitch;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import be.hogent.tarsos.sampled.pitch.Annotation;
import be.hogent.tarsos.sampled.pitch.AnnotationListener;
import be.hogent.tarsos.sampled.pitch.AnnotationPublisher;
import be.hogent.tarsos.sampled.pitch.PitchDetectionMode;
import be.hogent.tarsos.ui.pitch.ph.KDEData;
import be.hogent.tarsos.util.AudioFile;
import be.hogent.tarsos.util.histogram.Histogram;
import be.hogent.tarsos.util.histogram.peaks.Peak;
import be.hogent.tarsos.util.histogram.peaks.PeakDetector;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

public class CommandPanel extends JPanel implements AudioFileChangedListener, ScaleChangedListener, AnnotationListener{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -1310357618193806959L;
	
	private  int windowSizePeakDetection = 5;
	private  int thresholdPeakDetection = 15;
	private double[] scale;
	private final Set<PitchDetectionMode> pitchDetectors; 
	private final JComboBox pitchDetectorSelection;
	
	private final List<JComponent> listOfComponentsToDisableOrEnable;
	
	public CommandPanel(){
		pitchDetectors = new HashSet<PitchDetectionMode>();
		listOfComponentsToDisableOrEnable = new ArrayList<JComponent>();
		
		setMaximumSize(new Dimension(200, 1000));
		
		this.setLayout(new BorderLayout());
		
		JSlider probabilitySlider = new JSlider(0, 100);
		probabilitySlider.setValue(0);
		probabilitySlider.setMajorTickSpacing(1);
		probabilitySlider.addChangeListener(new ChangeListener() {

			public void stateChanged(final ChangeEvent e) {
				final JSlider source = (JSlider) e.getSource();
				final double newMinProbability = source.getValue() / 100.0;
				AnnotationPublisher.getInstance().clear();
				AnnotationPublisher.getInstance().alterSelection(newMinProbability);
				AnnotationPublisher.getInstance().delegateAddAnnotations(newMinProbability);
			}
		});
		listOfComponentsToDisableOrEnable.add(probabilitySlider);
		
		pitchDetectorSelection = new JComboBox();
		listOfComponentsToDisableOrEnable.add(pitchDetectorSelection);

		JSlider peakSlider = new JSlider(0, 100);
		peakSlider.setValue(windowSizePeakDetection);
		peakSlider.setMajorTickSpacing(20);
		peakSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				final JSlider source = (JSlider) e.getSource();
				windowSizePeakDetection = source.getValue();
				doPeakDetection(source.getValueIsAdjusting());
			}
		});
		listOfComponentsToDisableOrEnable.add(peakSlider);
		
		JSlider quantizeToScaleSlider = new JSlider(0, 150);
		quantizeToScaleSlider.setValue(15);
		quantizeToScaleSlider.setMajorTickSpacing(20);
		quantizeToScaleSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				final JSlider source = (JSlider) e.getSource();
				final double cents = source.getValue(); //cents
				AnnotationPublisher.getInstance().applyPitchClassFilter(scale, cents);
			}
		});
		listOfComponentsToDisableOrEnable.add(quantizeToScaleSlider);
			
		
		final JSlider centsSlider = new JSlider(0, 1200);
		centsSlider.setValue(thresholdPeakDetection);
		centsSlider.setMajorTickSpacing(20);			
		final JSlider timingSlider = new JSlider(0, 500);
		timingSlider.setValue(100);
		timingSlider.setMajorTickSpacing(20);								
		ChangeListener steadyStateChangeListener = new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				final double cents = centsSlider.getValue(); //cents
				final double time = timingSlider.getValue() / 1000.0; //seconds
				AnnotationPublisher.getInstance().applySteadyStateFilter(cents,time);
			}
		};
		centsSlider.addChangeListener(steadyStateChangeListener);	
		timingSlider.addChangeListener(steadyStateChangeListener);
		listOfComponentsToDisableOrEnable.add(centsSlider);
		listOfComponentsToDisableOrEnable.add(timingSlider);
		
		
		JSlider thresholdSlider = new JSlider(0, 100);
		thresholdSlider.setValue(thresholdPeakDetection);
		thresholdSlider.setMajorTickSpacing(20);
		thresholdSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				final JSlider source = (JSlider) e.getSource();
				thresholdPeakDetection = source.getValue();
				doPeakDetection(source.getValueIsAdjusting());
			}
		});
		listOfComponentsToDisableOrEnable.add(thresholdSlider);
		


		JButton resetButton = new JButton("Reset");
		resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				KDEData.getPitchClassHistogramInstance().clearHistograms();
				KDEData.getPitchClassHistogramInstance().repaint();
			}
		});
		listOfComponentsToDisableOrEnable.add(resetButton);


		FormLayout layout = new FormLayout("right:min,2dlu,min:grow");
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		//builder.setRowGroupingEnabled(true);
		
		
		
		builder.append("Pitch detector data:", pitchDetectorSelection, true);
		
		builder.appendSeparator("Peak picking");
		builder.append("Window:", peakSlider, true);
		builder.append("Threshold:", thresholdSlider, true);
		
		builder.appendSeparator("Steady state filter");
		builder.append("Time:", timingSlider, true);
		builder.append("Cents:", centsSlider, true);
		
		builder.appendSeparator("Annotation quality filter");
		builder.append("Quality:", probabilitySlider, true);
		
		builder.appendSeparator("Near to pitch class filter");
		builder.append("Quantize:", quantizeToScaleSlider, true);
		
		builder.appendSeparator("Histogram commands");
		builder.append("Smooth:", smoothButton, true);
		builder.append("Reset:", resetButton, true);
		
		
		this.add(new JScrollPane(builder.getPanel()));
		setEnabledForAll(false);
		
	}
	
	private void setEnabledForAll(boolean enabled){
		for(JComponent component : listOfComponentsToDisableOrEnable){
			component.setEnabled(enabled);
		}
	}

	public void audioFileChanged(AudioFile newAudioFile) {
		pitchDetectors.clear();
		rebuildPitchDetectorSelection();
	}
	
	private void rebuildPitchDetectorSelection(){
		pitchDetectorSelection.removeAllItems();
		for(PitchDetectionMode key : pitchDetectors)
			pitchDetectorSelection.addItem(key);
		if(!pitchDetectors.isEmpty()){
			setEnabledForAll(true);
		}
	}
	
	
	private void doPeakDetection(boolean detectorIsAdjusting){
		PitchDetectionMode selectedHistogram = (PitchDetectionMode) pitchDetectorSelection.getSelectedItem();
		Histogram histo = KDEData.getPitchClassHistogramInstance().getHistogram(selectedHistogram);
		
		if (histo.getMaxBinCount() != 0) {
			final List<Peak> peaks = PeakDetector.detect(histo, windowSizePeakDetection,thresholdPeakDetection);
			final double[] peaksInCents = new double[peaks.size()];
			int i = 0;
			for (final Peak peak : peaks) {
				peaksInCents[i++] = peak.getPosition();
			}
			Arrays.sort(peaksInCents);
			Frame.getInstance().scaleChanged(peaksInCents, detectorIsAdjusting, false);
		}
		
	}


	public void scaleChanged(double[] newScale, boolean isChanging,
			boolean shiftHisto) {
		scale = newScale;		
	}


	public void addAnnotation(Annotation annotation) {
		PitchDetectionMode key = annotation.getSource();
		if(!pitchDetectors.contains(key)){
			pitchDetectors.add(key);
			rebuildPitchDetectorSelection();			
		}
	}


	public void clearAnnotations() {
		// TODO Auto-generated method stub
		
	}


	public void annotationsAdded() {
		// TODO Auto-generated method stub
		
	}


	public void extractionStarted() {
		setEnabledForAll(false);
		
	}


	public void extractionFinished() {
		
	}
	
	
	

}
