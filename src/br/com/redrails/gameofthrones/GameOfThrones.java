package br.com.redrails.gameofthrones;

import java.io.IOException;


import org.anddev.andengine.audio.music.Music;
import org.anddev.andengine.audio.music.MusicFactory;
import org.anddev.andengine.engine.Engine;
import org.anddev.andengine.engine.camera.BoundCamera;
import org.anddev.andengine.engine.handler.IUpdateHandler;
import org.anddev.andengine.engine.options.EngineOptions;
import org.anddev.andengine.engine.options.EngineOptions.ScreenOrientation;
import org.anddev.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.anddev.andengine.entity.IEntity;
import org.anddev.andengine.entity.layer.tiled.tmx.TMXLayer;
import org.anddev.andengine.entity.layer.tiled.tmx.TMXLoader;
import org.anddev.andengine.entity.layer.tiled.tmx.TMXTiledMap;
import org.anddev.andengine.entity.layer.tiled.tmx.util.exception.TMXLoadException;
import org.anddev.andengine.entity.modifier.IEntityModifier;
import org.anddev.andengine.entity.modifier.PathModifier;
import org.anddev.andengine.entity.modifier.IEntityModifier.IEntityModifierListener;
import org.anddev.andengine.entity.modifier.PathModifier.IPathModifierListener;
import org.anddev.andengine.entity.modifier.PathModifier.Path;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.sprite.AnimatedSprite;
import org.anddev.andengine.entity.util.FPSLogger;
import org.anddev.andengine.opengl.texture.TextureOptions;
import org.anddev.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.anddev.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.anddev.andengine.opengl.texture.region.TiledTextureRegion;
import org.anddev.andengine.util.Debug;
import org.anddev.andengine.util.modifier.IModifier;


import android.util.Log;


public class GameOfThrones extends BaseGame {
	// ===========================================================
	// Constants
	// ===========================================================

	private static final int CAMERA_WIDTH = 480;
	private static final int CAMERA_HEIGHT = 320;
	private static final int TILE_WIDTH = 32;
	private static final int FRONT = 0;
	private static final int BACK = 1;
	private static final int LEFT = 2;
	private static final int RIGHT = 3;
	private static final float BASE_SPEED = 1.2f;
	private static final String TAG = "GAME OF THRONES";
	


	// ===========================================================
	// Fields
	// ===========================================================
	private static AnimatedSprite player;
	private static IEntityModifier mPathTemp;
	private static boolean stoped=true;

	static int mWaypointIndex=0;
	private BoundCamera mBoundChaseCamera;
	private int pathLeft = 6;
	private int[][] circuit = {{300,600,BACK},{300,160,BACK},{10,160,LEFT},{580,160,RIGHT},{300,160,LEFT},{300,160,FRONT}};
	private BitmapTextureAtlas mBitmapTextureAtlas;
	private TiledTextureRegion mPlayerTextureRegion;
	private TMXTiledMap mTMXTiledMap;
	protected int mCactusCount;
	private Music soundTrack;

	@Override
	public Engine onLoadEngine() {
		this.mBoundChaseCamera = new BoundCamera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);
		return new Engine(new EngineOptions(true, ScreenOrientation.LANDSCAPE, new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT), this.mBoundChaseCamera).setNeedsMusic(true));
	}

	@Override
	public void onLoadResources() {
		this.mBitmapTextureAtlas = new BitmapTextureAtlas(128, 128, TextureOptions.DEFAULT);
		BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("gfx/");
		MusicFactory.setAssetBasePath("mfx/");
		this.mPlayerTextureRegion = BitmapTextureAtlasTextureRegionFactory.createTiledFromAsset(this.mBitmapTextureAtlas, this, "player.png", 0, 0, 3, 4); // 3x4 tiles and 72x128
		
		try {
            this.soundTrack = MusicFactory.createMusicFromAsset(this.mEngine.getMusicManager(), this, "track.ogg");
            this.soundTrack.setLooping(true);

		} catch (final IOException e) {
			Log.e("SOUND ERROR", e.getMessage());
		}
		this.mEngine.getTextureManager().loadTexture(this.mBitmapTextureAtlas);
	}

	@Override
	public Scene onLoadScene() {
		this.mEngine.registerUpdateHandler(new FPSLogger());
		//GameOfThrones.this.soundTrack.setLooping(true);
		GameOfThrones.this.soundTrack.play();
		final Scene scene = new Scene();

		try {
			final TMXLoader tmxLoader = new TMXLoader(this, this.mEngine.getTextureManager(), TextureOptions.BILINEAR_PREMULTIPLYALPHA);			
			this.mTMXTiledMap = tmxLoader.loadFromAsset(this, "tmx/castle.tmx");
		} catch (final TMXLoadException tmxle) {
			Debug.e(tmxle);
		}

		final TMXLayer tmxLayer = this.mTMXTiledMap.getTMXLayers().get(0);
		scene.attachChild(tmxLayer);

		/* Make the camera not exceed the bounds of the TMXEntity. */
		this.mBoundChaseCamera.setBounds(0, tmxLayer.getWidth(), 0, tmxLayer.getHeight());
		this.mBoundChaseCamera.setBoundsEnabled(true);

		/* Calculate the coordinates for the face, so its centered on the camera. */
		final int centerX = (CAMERA_WIDTH - this.mPlayerTextureRegion.getTileWidth()) / 2;
		final int centerY = (CAMERA_HEIGHT - this.mPlayerTextureRegion.getTileHeight()) / 2;

		/* Create the sprite and add it to the scene. */
		player = new AnimatedSprite(centerX, centerY, this.mPlayerTextureRegion);
		this.mBoundChaseCamera.setChaseEntity(player);//Cam follow o Player
		player.setPosition(300,600);
		playerAnimate(BACK);
		walkToNextWayPoint(300, 160, scene);

		
		scene.registerUpdateHandler(new IUpdateHandler() {
			public void reset() { }

			public void onUpdate(final float pSecondsElapsed) {
				if(stoped){
					playerAnimate(FRONT);
					walkToNextWayPoint(300,600, scene);
				}
			}
		});		
		
		
		
		scene.attachChild(player);
		return scene;
	}
	@Override
	public void onLoadComplete() {
		
	}
	

	private static void walkToNextWayPoint(final float pX, final float pY, final Scene pScene) {

		
		stoped=false;

		// mPathTemp is another global PathModifier
		player.unregisterEntityModifier(mPathTemp);

		// create a new path with length 2 from current sprite position to next
		// original path waypoint
		final Path path = new Path(2);
		path.to(player.getX(), player.getY()).to(pX,pY);
		

		// recalculate the speed.TILE_WIDTH is the tmx tile width, use yours
		//Adjust the speed for different control options
		float TileSpeed = path.getLength() * BASE_SPEED / (TILE_WIDTH);
		Log.d(TAG,"SPEED: "+TileSpeed);

		// Create the modifier of this subpath
		mPathTemp = new PathModifier(TileSpeed, path, new IEntityModifierListener() {

			public void onModifierStarted(IModifier<IEntity> pModifier, IEntity pItem) {

			}

			public void onModifierFinished(IModifier<IEntity> pModifier, IEntity pItem) {

			}
		}, new IPathModifierListener() {

			public void onPathWaypointStarted(final PathModifier pPathModifier, final IEntity pEntity,
					int pWaypointIndex) {				

			}

			public void onPathWaypointFinished(PathModifier pPathModifier, IEntity pEntity, int pWaypointIndex) {
				
			}

			public void onPathStarted(PathModifier pPathModifier, IEntity pEntity) {

			}

			public void onPathFinished(PathModifier pPathModifier, IEntity pEntity) {
				player.stopAnimation();
				stoped=true;				
			}
		});

		player.registerEntityModifier(mPathTemp);
	}
	
	private static void playerAnimate(int position){
		switch(position) {
		case FRONT:
			player.animate(new long[]{200, 200, 200}, 6, 8, true);
			break;
		case RIGHT:
			player.animate(new long[]{200, 200, 200}, 3, 5, true);
			break;
		case BACK:
			player.animate(new long[]{200, 200, 200}, 0, 2, true);
			break;
		case LEFT:
			player.animate(new long[]{200, 200, 200}, 9, 11, true);
			break;
	}
		
	}
	

}