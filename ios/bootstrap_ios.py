#!/usr/bin/env python3
import os
import uuid

def generate_xcode_uuid():
    # Xcode UUIDs are 24-character hex strings
    return uuid.uuid4().hex[:24].upper()

def main():
    print("=== Khởi tạo dự án Xcode cho ứng dụng Trợ Lý Nhạc ===")
    
    # Define directories
    ios_dir = os.path.dirname(os.path.abspath(__file__))
    project_dir = os.path.join(ios_dir, "TroLyNhac.xcodeproj")
    os.makedirs(project_dir, exist_ok=True)
    
    # Source files list (filenames only, relative to TroLyNhac/)
    source_files = [
        "TroLyNhacApp.swift",
        "Models.swift",
        "Theme.swift",
        "AudioDeviceHelper.swift",
        "AudioDecisionEngine.swift",
        "SubsonicRepository.swift",
        "PlayerManager.swift",
    ]
    
    # Views files list (filenames only, relative to TroLyNhac/Views/)
    views_files = [
        "LoginView.swift",
        "HomeView.swift",
        "SearchView.swift",
        "LibraryView.swift",
        "SettingsView.swift",
        "DetailViews.swift",
        "MiniPlayerView.swift",
        "NowPlayingView.swift",
        "AppShellView.swift",
    ]
    
    # Generate UUIDs for all objects to keep the project consistent
    id_project = generate_xcode_uuid()
    id_main_group = generate_xcode_uuid()
    id_app_group = generate_xcode_uuid()
    id_views_group = generate_xcode_uuid()
    id_products_group = generate_xcode_uuid()
    
    id_target = generate_xcode_uuid()
    id_product_ref = generate_xcode_uuid()
    
    id_sources_build_phase = generate_xcode_uuid()
    id_frameworks_build_phase = generate_xcode_uuid()
    id_resources_build_phase = generate_xcode_uuid()
    
    id_config_list_target = generate_xcode_uuid()
    id_config_debug_target = generate_xcode_uuid()
    id_config_release_target = generate_xcode_uuid()
    
    id_config_list_project = generate_xcode_uuid()
    id_config_debug_project = generate_xcode_uuid()
    id_config_release_project = generate_xcode_uuid()
    
    # File References and Build Files mappings
    file_refs = {}
    build_files = {}
    
    # Info.plist reference
    id_plist_ref = generate_xcode_uuid()
    file_refs["Info.plist"] = id_plist_ref
    
    # Assets catalog reference
    id_assets_ref = generate_xcode_uuid()
    id_assets_build_id = generate_xcode_uuid()
    
    # Source files mapping
    for f in source_files:
        ref_id = generate_xcode_uuid()
        build_id = generate_xcode_uuid()
        file_refs[f] = ref_id
        build_files[f] = (ref_id, build_id)
        
    for f in views_files:
        ref_id = generate_xcode_uuid()
        build_id = generate_xcode_uuid()
        file_refs[f] = ref_id
        build_files[f] = (ref_id, build_id)
        
    # App Product reference
    file_refs["TroLyNhac.app"] = id_product_ref
    
    # ── GENERATE project.pbxproj CONTENT ───────────────────────────────────
    
    # Begin PBXProj content
    content = f"""// !$*UTF8*$!
{{
	archiveVersion = 1;
	classes = {{
	}};
	objectVersion = 56;
	objects = {{

/* Begin PBXBuildFile section */
"""
    
    # Write compile sources entries
    for f in source_files + views_files:
        ref_id, build_id = build_files[f]
        content += f"\t\t{build_id} /* {f} in Sources */ = {{isa = PBXBuildFile; fileRef = {ref_id} /* {f} */; }};\n"
        
    # Write Assets catalog entry under PBXBuildFile section
    content += f"\t\t{id_assets_build_id} /* Assets.xcassets in Resources */ = {{isa = PBXBuildFile; fileRef = {id_assets_ref} /* Assets.xcassets */; }};\n"

    content += """/* End PBXBuildFile section */

/* Begin PBXFileReference section */
"""
    
    # Write App reference
    content += f"\t\t{id_product_ref} /* TroLyNhac.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = TroLyNhac.app; sourceTree = BUILT_PRODUCTS_DIR; }};\n"
    
    # Write Info.plist reference (relative to group path, which is TroLyNhac)
    content += f"\t\t{id_plist_ref} /* Info.plist */ = {{isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = \"<group>\"; }};\n"
    
    # Write Assets catalog reference
    content += f"\t\t{id_assets_ref} /* Assets.xcassets */ = {{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = \"<group>\"; }};\n"

    # Write Swift file references for main source files
    for f in source_files:
        ref_id = file_refs[f]
        content += f"\t\t{ref_id} /* {f} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = {f}; sourceTree = \"<group>\"; }};\n"
        
    # Write Swift file references for views files
    for f in views_files:
        ref_id = file_refs[f]
        content += f"\t\t{ref_id} /* {f} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = {f}; sourceTree = \"<group>\"; }};\n"
        
    content += f"""/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
		{id_frameworks_build_phase} /* Frameworks */ = {{
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		}};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		{id_main_group} = {{
			isa = PBXGroup;
			children = (
				{id_app_group} /* TroLyNhac */,
				{id_products_group} /* Products */,
			);
			sourceTree = "<group>";
		}};
		{id_products_group} /* Products */ = {{
			isa = PBXGroup;
			children = (
				{id_product_ref} /* TroLyNhac.app */,
			);
			name = Products;
			sourceTree = "<group>";
		}};
		{id_app_group} /* TroLyNhac */ = {{
			isa = PBXGroup;
			children = (
				{id_plist_ref} /* Info.plist */,
				{id_assets_ref} /* Assets.xcassets */,
"""
    
    # Add flat references to main files under app group
    for f in source_files:
        ref_id = file_refs[f]
        content += f"\t\t\t\t{ref_id} /* {f} */,\n"
        
    content += f"\t\t\t\t{id_views_group} /* Views */,\n"
    content += f"""\t\t\t);
			name = TroLyNhac;
			path = TroLyNhac;
			sourceTree = "<group>";
		}};
		{id_views_group} /* Views */ = {{
			isa = PBXGroup;
			children = (
"""
    
    # Add references to views group
    for f in views_files:
        ref_id = file_refs[f]
        content += f"\t\t\t\t{ref_id} /* {f} */,\n"
        
    content += f"""\t\t\t);
			name = Views;
			path = Views;
			sourceTree = "<group>";
		}};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
		{id_target} /* TroLyNhac */ = {{
			isa = PBXNativeTarget;
			buildConfigurationList = {id_config_list_target} /* Build configuration list for PBXNativeTarget "TroLyNhac" */;
			buildPhases = (
				{id_sources_build_phase} /* Sources */,
				{id_frameworks_build_phase} /* Frameworks */,
				{id_resources_build_phase} /* Resources */,
			);
			buildRules = (
			);
			dependencies = (
			);
			name = TroLyNhac;
			productName = TroLyNhac;
			productReference = {id_product_ref} /* TroLyNhac.app */;
			productType = "com.apple.product-type.application";
		}};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
		{id_project} /* Project object */ = {{
			isa = PBXProject;
			attributes = {{
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 1400;
				LastUpgradeCheck = 1400;
				TargetAttributes = {{
					{id_target} = {{
						CreatedOnToolsVersion = 14.0;
						DevelopmentTeam = "";
						LastSwiftMigration = 1400;
					}};
				}};
			}};
			buildConfigurationList = {id_config_list_project} /* Build configuration list for PBXProject "TroLyNhac" */;
			compatibilityVersion = "Xcode 14.0";
			developmentRegion = vi;
			hasScannedForEncodings = 0;
			knownRegions = (
				vi,
				Base,
			);
			mainGroup = {id_main_group};
			productRefGroup = {id_products_group} /* Products */;
			projectDirPath = "";
			projectRoot = "";
			targets = (
				{id_target} /* TroLyNhac */,
			);
		}};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
		{id_resources_build_phase} /* Resources */ = {{
			isa = PBXResourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
				{id_assets_build_id} /* Assets.xcassets in Resources */,
			);
			runOnlyForDeploymentPostprocessing = 0;
		}};
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
		{id_sources_build_phase} /* Sources */ = {{
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
"""
    
    # Write compile references into target build sources
    for f in source_files + views_files:
        _, build_id = build_files[f]
        content += f"\t\t\t\t{build_id} /* {f} in Sources */,\n"
        
    content += f"""\t\t\t);
			runOnlyForDeploymentPostprocessing = 0;
		}};
/* End PBXSourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
		{id_config_debug_project} /* Debug */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_ANALYZER_NONNULL = YES;
				CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_CXX_LIBRARY = "libc++";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_ENABLE_OBJC_WEAK = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_PREPATENT_COERCION = YES_ABSOLUTE;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = dwarf;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_TESTABILITY = YES;
				GCC_C_LANGUAGE_STANDARD = gnu11;
				GCC_DYNAMIC_NO_PIC = NO;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZATION_LEVEL = 0;
				GCC_PREPROCESSOR_DEFINITIONS = (
					"DEBUG=1",
					"$(inherited)",
				);
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				MTL_FAST_MATH = YES;
				ONLY_ACTIVE_ARCH = YES;
				SDKROOT = iphoneos;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
			}};
			name = Debug;
		}};
		{id_config_release_project} /* Release */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_ANALYZER_NONNULL = YES;
				CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_CXX_LIBRARY = "libc++";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_ENABLE_OBJC_WEAK = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_PREPATENT_COERCION = YES_ABSOLUTE;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				COPY_PHASE_STRIP = YES;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				GCC_C_LANGUAGE_STANDARD = gnu11;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				MTL_ENABLE_DEBUG_INFO = NO;
				MTL_FAST_MATH = YES;
				SDKROOT = iphoneos;
				SWIFT_COMPILATION_MODE = wholemodule;
				SWIFT_OPTIMIZATION_LEVEL = "-O";
				VALIDATE_PRODUCT = YES;
			}};
			name = Release;
		}};
		{id_config_debug_target} /* Debug */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				GENERATE_INFOPLIST_FILE = NO;
				INFOPLIST_FILE = TroLyNhac/Info.plist;
				INFOPLIST_KEY_LSRequiresIPhoneOS = YES;
				INFOPLIST_KEY_UIApplicationSceneManifest_UIApplicationSupportsMultipleScenes = NO;
				INFOPLIST_KEY_UILaunchScreen_UIApplicationSupportsIndirectInputEvents = YES;
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				MARKETING_VERSION = 1.0.0;
				PRODUCT_BUNDLE_IDENTIFIER = me.troly.nhac.ios;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			}};
			name = Debug;
		}};
		{id_config_release_target} /* Release */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				GENERATE_INFOPLIST_FILE = NO;
				INFOPLIST_FILE = TroLyNhac/Info.plist;
				INFOPLIST_KEY_LSRequiresIPhoneOS = YES;
				INFOPLIST_KEY_UIApplicationSceneManifest_UIApplicationSupportsMultipleScenes = NO;
				INFOPLIST_KEY_UILaunchScreen_UIApplicationSupportsIndirectInputEvents = YES;
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				MARKETING_VERSION = 1.0.0;
				PRODUCT_BUNDLE_IDENTIFIER = me.troly.nhac.ios;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			}};
			name = Release;
		}};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
		{id_config_list_target} /* Build configuration list for PBXNativeTarget "TroLyNhac" */ = {{
			isa = XCConfigurationList;
			buildConfigurations = (
				{id_config_debug_target} /* Debug */,
				{id_config_release_target} /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		}};
		{id_config_list_project} /* Build configuration list for PBXProject "TroLyNhac" */ = {{
			isa = XCConfigurationList;
			buildConfigurations = (
				{id_config_debug_project} /* Debug */,
				{id_config_release_project} /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		}};
/* End XCConfigurationList section */
	}};
	rootObject = {id_project} /* Project object */;
}}
"""
    
    # Write the pbxproj file
    pbxproj_path = os.path.join(project_dir, "project.pbxproj")
    with open(pbxproj_path, "w", encoding="utf-8") as f:
        f.write(content)
        
    print(f"-> Đã khởi tạo tệp cấu hình dự án tại: {pbxproj_path}")
    print("Dự án đã sẵn sàng để biên dịch bằng xcodebuild hoặc mở bằng Xcode!")

if __name__ == "__main__":
    main()
