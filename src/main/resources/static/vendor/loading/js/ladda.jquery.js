/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function( Ladda, $ ) {
	if ($ === undefined)
		return console.error( 'jQuery required for Ladda.jQuery' );

	var arr = [];

	$ = $.extend( $, {
		ladda: function( arg ) {
			if( arg === 'stopAll' )
				Ladda.stopAll();
		}
	});

	$.fn = $.extend( $.fn, {
		ladda: function( arg ) {
			var args = arr.slice.call( arguments, 1 );

			if( arg === 'bind' ) {
				args.unshift( $( this ).selector );
				Ladda.bind.apply( Ladda, args );
			}
			else {
				$( this ).each( function() {
					var $this = $( this ), ladda;

					if( arg === undefined )
						$this.data( 'ladda', Ladda.create( this ) );
					else {
						ladda = $this.data( 'ladda' );
						ladda[arg].apply( ladda, args );
					}
				});
			}

			return this;
		}
	});	
}( this.Ladda, this.jQuery ));